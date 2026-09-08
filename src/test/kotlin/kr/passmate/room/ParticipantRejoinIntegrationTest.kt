package kr.passmate.room

import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * PIN 없는 재입장 (시나리오 테스트 "세션이 끝나기 전까지는 핀 없이 입장", 2026-09-08).
 *
 * 새 참가자 행을 만들면 점수·답안이 두 사람으로 갈라진다 — 원래 행을 되살리는지 본다.
 */
@AutoConfigureMockMvc
@Transactional
class ParticipantRejoinIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var sessionService: SessionService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var hostId: Long = 0
    private var studentId: Long = 0
    private var roomId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String
    private var studentParticipantId: Long = 0

    @BeforeEach
    fun setUp() {
        hostId = member("rejoin-host")
        studentId = member("rejoin-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("재입장 테스트"))
        questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.OX, "TCP 는 연결지향이다", answer = "O", timeLimitSec = 30, points = 100),
        )
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "재입장", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "재입장", questionSetId = set.id))
        roomId = room.id
        studentParticipantId = participantService
            .join(roomId, studentId, JoinRoomRequest(nickname = "학생")).participant.id
    }

    @Test
    fun `나갔던 회원이 진행 중인 방에 PIN 없이 돌아온다`() {
        sessionService.start(roomId, hostId)
        participantService.leave(roomId, kr.passmate.common.security.UserPrincipal(studentId, false))

        rejoin(studentToken)
            .andExpect(status().isOk)
            // 같은 참가자 행이라 점수·답안이 이어진다
            .andExpect(jsonPath("$.participant.id").value(studentParticipantId))
            .andExpect(jsonPath("$.participant.nickname").value("학생"))

        // 명단에 다시 보인다
        mockMvc.perform(get("/rooms/{id}/participants", roomId).header(AUTH, "Bearer $hostToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.nickname=='학생')]").isNotEmpty)
    }

    @Test
    fun `나가지 않았어도 재입장은 같은 행을 돌려준다`() {
        rejoin(studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participant.id").value(studentParticipantId))
    }

    @Test
    fun `게스트는 만료를 대비해 토큰을 새로 받는다`() {
        val guest = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트"))
        participantService.leave(roomId, GuestPrincipal(guest.participant.id, roomId))

        val body = rejoin(guest.accessToken!!).andExpect(status().isOk).andReturn()
            .let { objectMapper.readTree(it.response.contentAsString) }

        assertThat(body.get("participant").get("id").asLong()).isEqualTo(guest.participant.id)
        assertThat(body.get("accessToken").asText()).isNotBlank()
    }

    @Test
    fun `강퇴당한 사람은 돌아올 수 없다`() {
        participantService.kick(roomId, studentParticipantId, hostId)

        rejoin(studentToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `끝난 방에는 재입장할 수 없다`() {
        sessionService.start(roomId, hostId)
        sessionService.end(roomId, hostId)

        rejoin(studentToken)
            .andExpect(status().isGone)
            .andExpect(jsonPath("$.code").value("ROOM_ENDED"))
    }

    @Test
    fun `들어간 적 없는 방에는 재입장할 수 없다`() {
        val outsider = jwtTokenProvider.issue(member("rejoin-outsider"), false).accessToken

        rejoin(outsider)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_FOUND"))
    }

    private fun rejoin(token: String) = mockMvc.perform(
        post("/rooms/{id}/participants/me/rejoin", roomId).header(AUTH, "Bearer $token"),
    )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, null, key, null).user.id

    private companion object {
        const val AUTH = "Authorization"
    }
}
