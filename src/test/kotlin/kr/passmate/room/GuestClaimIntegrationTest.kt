package kr.passmate.room

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.repository.ParticipantRepository
import kr.passmate.room.repository.RoomRepository
import kr.passmate.room.service.ParticipantService
import kr.passmate.room.service.RoomService
import kr.passmate.session.service.SessionService
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 게스트 기록의 계정 연동 (FR-036 · FR-037, US9).
 *
 * 게스트가 세션을 마치고 가입하면 방금 기록이 계정에 붙는다.
 */
@AutoConfigureMockMvc
@Transactional
class GuestClaimIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var sessionService: SessionService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var participantRepository: ParticipantRepository
    @Autowired private lateinit var roomRepository: RoomRepository

    private var hostId: Long = 0
    private var setId: Long = 0
    private var questionId: Long = 0
    private var roomId: Long = 0
    private var newMemberId: Long = 0
    private lateinit var hostToken: String
    private lateinit var newMemberToken: String
    private lateinit var guestToken: String
    private lateinit var guestAccessToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("claim-host")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        newMemberId = member("claim-newbie")
        newMemberToken = jwtTokenProvider.issue(newMemberId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("전환 테스트"))
        questionId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.OX, "참인가", answer = "O", timeLimitSec = 20, points = 50),
        ).id
        questionSetService.confirm(set.id, hostId)
        setId = set.id

        val room = roomService.create(hostId, RoomCreateRequest(title = "전환 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "전환 방", questionSetId = setId))
        roomId = room.id

        val joined = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트"))
        guestToken = joined.guestToken!!
        guestAccessToken = joined.accessToken!!
    }

    @Test
    fun `가입 직후 게스트 기록을 계정에 붙인다`() {
        playSession()

        val body = claim(newMemberToken, guestToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("roomId").asLong()).isEqualTo(roomId)
        assertThat(body.get("roomTitle").asText()).isEqualTo("전환 방")
        assertThat(body.get("nickname").asText()).isEqualTo("게스트")
        assertThat(body.get("claimedAt").isNull).isFalse()

        val participant = participantRepository.findByGuestToken(guestToken)!!
        assertThat(participant.userId).isEqualTo(newMemberId)
    }

    @Test
    fun `연동한 기록은 참여한 방 목록에 나온다`() {
        playSession()
        claim(newMemberToken, guestToken).andExpect(status().isOk)

        mockMvc.perform(get("/users/me/rooms/joined").header(AUTH, "Bearer $newMemberToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rooms.content[0].roomId").value(roomId))
    }

    @Test
    fun `닉네임은 그대로 둔다 — 그 세션에서 남들이 본 이름이다`() {
        playSession()
        claim(newMemberToken, guestToken).andExpect(status().isOk)

        assertThat(participantRepository.findByGuestToken(guestToken)!!.nickname).isEqualTo("게스트")
    }

    @Test
    fun `같은 토큰을 두 번 제출하면 거절한다`() {
        playSession()
        claim(newMemberToken, guestToken).andExpect(status().isOk)

        val other = jwtTokenProvider.issue(member("claim-other"), false).accessToken
        claim(other, guestToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GUEST_RECORD_ALREADY_CLAIMED"))
    }

    @Test
    fun `보관 기한이 지난 기록은 파기된 것으로 안내한다`() {
        playSession()
        // 종료 시각을 보관 기한 너머로 밀어 둔다
        val room = roomRepository.findById(roomId).get()
        val endedAt = java.time.LocalDateTime.now().minusDays(30)
        setEndedAt(room, endedAt)

        claim(newMemberToken, guestToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GUEST_RECORD_EXPIRED"))
    }

    @Test
    fun `아직 안 끝난 세션도 연동할 수 있다`() {
        claim(newMemberToken, guestToken).andExpect(status().isOk)
    }

    @Test
    fun `이미 회원으로 참여한 방이면 거절한다`() {
        participantService.join(roomId, newMemberId, JoinRoomRequest(nickname = "회원"))
        playSession()

        claim(newMemberToken, guestToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    @Test
    fun `없는 토큰은 404 다`() {
        claim(newMemberToken, "존재하지-않는-토큰")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `빈 토큰은 거절한다`() {
        claim(newMemberToken, "  ").andExpect(status().isBadRequest)
    }

    @Test
    fun `게스트 토큰으로는 연동을 부를 수 없다`() {
        playSession()

        // 기록을 받을 계정이 있어야 옮길 곳이 정해진다
        claim(guestAccessToken, guestToken).andExpect(status().isForbidden)
    }

    @Test
    fun `로그인하지 않으면 연동할 수 없다`() {
        mockMvc.perform(
            post("/guest-records/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("guestToken" to guestToken))),
        ).andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    private fun playSession() {
        sessionService.start(roomId, hostId)
        sessionService.end(roomId, hostId)
    }

    /** endedAt 은 세션이 정하는 값이라 setter 가 없다. 기한 경과만 만들려고 리플렉션으로 민다. */
    private fun setEndedAt(room: kr.passmate.room.domain.Room, at: java.time.LocalDateTime) {
        val field = kr.passmate.room.domain.Room::class.java.getDeclaredField("endedAt")
        field.isAccessible = true
        field.set(room, at)
        roomRepository.saveAndFlush(room)
    }

    private fun claim(authToken: String, guest: String): ResultActions =
        mockMvc.perform(
            post("/guest-records/claim")
                .header(AUTH, "Bearer $authToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("guestToken" to guest))),
        )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
