package kr.passmate.room

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class RoomIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var hostToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setUp() {
        hostToken = tokenFor("host")
        otherToken = tokenFor("other")
    }

    // ---------- 방 개설 ----------

    @Test
    fun `방을 만들면 6자리 PIN 이 발급된다`() {
        createRoom()
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("CS 면접 대비"))
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.type").value("FREE"))
            .andExpect(jsonPath("$.pin").value(org.hamcrest.Matchers.matchesRegex("\\d{6}")))
            .andExpect(jsonPath("$.participantCount").value(0))
    }

    @Test
    fun `호스트가 아니면 방을 수정할 수 없다`() {
        val roomId = createdRoom().get("id").asLong()

        mockMvc.perform(
            put("/rooms/{id}", roomId).header("Authorization", "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"가로채기"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `방을 닫으면 취소 상태가 되고 PIN 으로 더 찾을 수 없다`() {
        val room = createdRoom()
        val roomId = room.get("id").asLong()
        val pin = room.get("pin").asText()

        mockMvc.perform(post("/rooms/{id}/close", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))

        mockMvc.perform(get("/rooms/pin/{pin}", pin))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
    }

    @Test
    fun `QR 은 PNG 로 내려오고 호스트만 받을 수 있다`() {
        val roomId = createdRoom().get("id").asLong()

        mockMvc.perform(get("/rooms/{id}/qr", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))

        mockMvc.perform(get("/rooms/{id}/qr", roomId).header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isForbidden)
    }

    // ---------- 방 입장 ----------

    @Test
    fun `PIN 조회와 게스트 입장은 인증 없이 된다`() {
        val pin = createdRoom().get("pin").asText()

        val summary = mockMvc.perform(get("/rooms/pin/{pin}", pin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.guestAllowed").value(true))
            .andReturn().json()
        val roomId = summary.get("id").asLong()

        mockMvc.perform(
            post("/rooms/{id}/participants", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"게스트1","avatarId":"cat"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.participant.isGuest").value(true))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.guestToken").isNotEmpty)
    }

    @Test
    fun `회원이 입장하면 게스트 토큰을 받지 않는다`() {
        val roomId = createdRoom().get("id").asLong()

        mockMvc.perform(
            post("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"회원참가자"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.participant.isGuest").value(false))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.guestToken").doesNotExist())
    }

    @Test
    fun `같은 방에서 닉네임은 중복될 수 없고 대안을 제안한다`() {
        val roomId = createdRoom().get("id").asLong()
        joinAsGuest(roomId, "혜림").andExpect(status().isCreated)

        joinAsGuest(roomId, "혜림")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"))

        mockMvc.perform(get("/rooms/{id}/participants/nickname-check", roomId).param("nickname", "혜림"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.suggestions[0]").value("혜림2"))

        mockMvc.perform(get("/rooms/{id}/participants/nickname-check", roomId).param("nickname", "혜림2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(true))
    }

    @Test
    fun `정원이 차면 입장을 거부한다`() {
        val roomId = createdRoom(maxParticipants = 1).get("id").asLong()
        joinAsGuest(roomId, "첫번째").andExpect(status().isCreated)

        joinAsGuest(roomId, "두번째")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ROOM_FULL"))
    }

    @Test
    fun `회원은 같은 방에 두 번 입장할 수 없다`() {
        val roomId = createdRoom().get("id").asLong()
        joinAsMember(roomId, "회원", otherToken).andExpect(status().isCreated)

        joinAsMember(roomId, "회원둘", otherToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_JOINED"))
    }

    @Test
    fun `게스트는 자기 토큰으로 참가자 목록을 보고 퇴장할 수 있다`() {
        val roomId = createdRoom().get("id").asLong()
        val guestToken = joinAsGuest(roomId, "게스트1").andReturn().json()
            .get("accessToken").asText()

        mockMvc.perform(get("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $guestToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))

        mockMvc.perform(delete("/rooms/{id}/participants/me", roomId).header("Authorization", "Bearer $guestToken"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $guestToken"))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `게스트 토큰으로는 회원 전용 API 를 부를 수 없다`() {
        val roomId = createdRoom().get("id").asLong()
        val guestToken = joinAsGuest(roomId, "게스트1").andReturn().json()
            .get("accessToken").asText()

        // 방 생성은 회원 전용
        mockMvc.perform(
            post("/rooms").header("Authorization", "Bearer $guestToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"게스트가 만든 방"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("GUEST_NOT_ALLOWED"))
    }

    @Test
    fun `호스트는 참가자를 내보낼 수 있고 다른 사람은 못 한다`() {
        val roomId = createdRoom().get("id").asLong()
        val participantId = joinAsGuest(roomId, "내보낼사람").andReturn().json()
            .get("participant").get("id").asLong()

        mockMvc.perform(
            delete("/rooms/{id}/participants/{pid}", roomId, participantId)
                .header("Authorization", "Bearer $otherToken"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))

        mockMvc.perform(
            delete("/rooms/{id}/participants/{pid}", roomId, participantId)
                .header("Authorization", "Bearer $hostToken"),
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `종료된 방에는 입장할 수 없다`() {
        val roomId = createdRoom().get("id").asLong()
        mockMvc.perform(post("/rooms/{id}/close", roomId).header("Authorization", "Bearer $hostToken"))

        joinAsGuest(roomId, "지각생")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ROOM_NOT_JOINABLE"))
    }

    // ---------- helpers ----------

    private fun tokenFor(key: String): String {
        val outcome = userService.loginOrRegister(
            provider = AuthProvider.GOOGLE,
            providerId = "test-$key",
            email = "$key@example.com",
            name = key,
            profileImageUrl = null,
        )
        return jwtTokenProvider.issue(outcome.user.id, outcome.user.isAdmin).accessToken
    }

    private fun createRoom(maxParticipants: Int? = null): ResultActions {
        val max = maxParticipants?.let { ""","maxParticipants":$it""" } ?: ""
        return mockMvc.perform(
            post("/rooms").header("Authorization", "Bearer $hostToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"CS 면접 대비","topic":"CS 면접"$max}"""),
        )
    }

    private fun createdRoom(maxParticipants: Int? = null): JsonNode =
        createRoom(maxParticipants).andExpect(status().isCreated).andReturn().json()

    private fun joinAsGuest(roomId: Long, nickname: String) = mockMvc.perform(
        post("/rooms/{id}/participants", roomId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"nickname":"$nickname","avatarId":"cat"}"""),
    )

    private fun joinAsMember(roomId: Long, nickname: String, token: String) = mockMvc.perform(
        post("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"nickname":"$nickname"}"""),
    )

    private fun org.springframework.test.web.servlet.MvcResult.json(): JsonNode =
        objectMapper.readTree(response.contentAsString)
}
