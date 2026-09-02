package kr.passmate.moderation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.service.RoomService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 차단이 실제로 무엇을 막는가 (FR-054 · FR-067, 엣지 케이스 23).
 *
 * 차단은 **목록 노출과 프로필 접근만** 막는다. PIN 직접 입장은 열어 둔다.
 */
@AutoConfigureMockMvc
@Transactional
class BlockEffectIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var hostId: Long = 0
    private lateinit var myToken: String
    private lateinit var pin: String

    @BeforeEach
    fun setUp() {
        val meId = member("effect-me")
        myToken = jwtTokenProvider.issue(meId, false).accessToken
        hostId = member("effect-host")

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("차단 효과"))
        questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.OX, "참인가", answer = "O", timeLimitSec = 20, points = 50),
        )
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "차단당할 방", type = RoomType.FREE))
        roomService.update(
            room.id, hostId,
            RoomUpdateRequest(title = "차단당할 방", questionSetId = set.id, isPublic = true),
        )
        pin = room.pin
    }

    @Test
    fun `차단하기 전에는 공개 목록에 보인다`() {
        assertThat(publicRoomTitles(myToken)).contains("차단당할 방")
    }

    @Test
    fun `차단한 호스트의 방은 공개 목록에서 빠진다`() {
        block()

        assertThat(publicRoomTitles(myToken)).doesNotContain("차단당할 방")
    }

    @Test
    fun `차단은 나에게만 적용된다 — 남의 목록은 그대로다`() {
        block()

        val otherToken = jwtTokenProvider.issue(member("effect-other"), false).accessToken
        assertThat(publicRoomTitles(otherToken)).contains("차단당할 방")
        // 로그인하지 않은 사람에게도 그대로 보인다
        assertThat(publicRoomTitles(null)).contains("차단당할 방")
    }

    @Test
    fun `차단한 호스트의 프로필은 볼 수 없다`() {
        block()

        mockMvc.perform(get("/users/{id}/profile", hostId).header(AUTH, "Bearer $myToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `차단해도 남들은 그 프로필을 본다`() {
        block()

        mockMvc.perform(get("/users/{id}/profile", hostId)).andExpect(status().isOk)
    }

    @Test
    fun `차단해도 PIN 으로는 그대로 들어간다`() {
        block()

        // 목록·프로필만 막는다. PIN 을 받아 온 사람은 그 방을 쓰기로 한 것이다
        mockMvc.perform(get("/rooms/pin/{pin}", pin).header(AUTH, "Bearer $myToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("차단당할 방"))
    }

    // ---------- helpers ----------

    private fun block() =
        mockMvc.perform(post("/users/{id}/block", hostId).header(AUTH, "Bearer $myToken"))
            .andExpect(status().isNoContent)

    private fun publicRoomTitles(token: String?): List<String> {
        val request = get("/rooms/public")
        token?.let { request.header(AUTH, "Bearer $it") }
        val body = mockMvc.perform(request).andExpect(status().isOk).andReturn().json()
        return body.get("content").map { it.get("title").asText() }
    }

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
