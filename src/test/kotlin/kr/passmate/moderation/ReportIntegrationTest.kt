package kr.passmate.moderation

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
import kr.passmate.room.service.ParticipantService
import kr.passmate.room.service.RoomService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 신고 접수 (FR-067). 참가자·호스트·문제·방을 신고할 수 있고 게스트는 익명이다.
 */
@AutoConfigureMockMvc
@Transactional
class ReportIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var reporterId: Long = 0
    private var hostId: Long = 0
    private var roomId: Long = 0
    private var questionId: Long = 0
    private var participantId: Long = 0
    private lateinit var reporterToken: String
    private lateinit var guestToken: String

    @BeforeEach
    fun setUp() {
        reporterId = member("report-me")
        hostId = member("report-host")
        reporterToken = jwtTokenProvider.issue(reporterId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("신고 테스트"))
        questionId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.OX, "참인가", answer = "O", timeLimitSec = 20, points = 50),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "신고 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "신고 방", questionSetId = set.id))
        roomId = room.id

        participantId = participantService.join(roomId, reporterId, JoinRoomRequest(nickname = "신고자")).participant.id
        guestToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트")).accessToken!!
    }

    @Test
    fun `호스트를 신고하면 미처리로 접수된다`() {
        val body = report(reporterToken, "USER", hostId, "OPERATION", "진행이 불성실합니다")
            .andExpect(status().isCreated).andReturn().json()

        assertThat(body.get("id").asLong()).isPositive()
        assertThat(body.get("targetType").asText()).isEqualTo("USER")
        assertThat(body.get("targetId").asLong()).isEqualTo(hostId)
        assertThat(body.get("type").asText()).isEqualTo("OPERATION")
        assertThat(body.get("status").asText()).isEqualTo("OPEN")
        assertThat(body.get("createdAt").isNull).isFalse()
    }

    @Test
    fun `참가자 · 문제 · 방도 신고할 수 있다`() {
        report(reporterToken, "PARTICIPANT", participantId, "NICKNAME", "닉네임이 부적절합니다")
            .andExpect(status().isCreated)
        report(reporterToken, "QUESTION", questionId, "QUESTION_ERROR", "정답이 틀렸습니다")
            .andExpect(status().isCreated)
        report(reporterToken, "ROOM", roomId, "SPAM", "도배가 심합니다")
            .andExpect(status().isCreated)
    }

    @Test
    fun `게스트도 익명으로 신고할 수 있다`() {
        report(guestToken, "USER", hostId, "OPERATION", "진행이 불성실합니다")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("OPEN"))
    }

    @Test
    fun `로그인하지 않으면 신고할 수 없다`() {
        mockMvc.perform(
            post("/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("USER", hostId, "OPERATION", "사유")),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `사유는 비울 수 없다`() {
        report(reporterToken, "USER", hostId, "OPERATION", "   ").andExpect(status().isBadRequest)
    }

    @Test
    fun `모르는 대상 유형은 거절한다`() {
        report(reporterToken, "COMMENT", hostId, "OPERATION", "사유").andExpect(status().isBadRequest)
    }

    @Test
    fun `모르는 신고 유형은 거절한다`() {
        report(reporterToken, "USER", hostId, "MOOD", "사유").andExpect(status().isBadRequest)
    }

    @Test
    fun `없는 사용자는 신고할 수 없다`() {
        report(reporterToken, "USER", 999_999, "OPERATION", "사유")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `없는 방은 신고할 수 없다`() {
        report(reporterToken, "ROOM", 999_999, "SPAM", "사유")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
    }

    @Test
    fun `자기 자신은 신고할 수 없다`() {
        report(reporterToken, "USER", reporterId, "OPERATION", "사유")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `같은 대상을 두 번 신고할 수 없다`() {
        report(reporterToken, "USER", hostId, "OPERATION", "처음").andExpect(status().isCreated)

        report(reporterToken, "USER", hostId, "SPAM", "또 신고")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    // ---------- helpers ----------

    private fun payload(targetType: String, targetId: Long, type: String, reason: String) =
        objectMapper.writeValueAsString(
            mapOf("targetType" to targetType, "targetId" to targetId, "type" to type, "reason" to reason),
        )

    private fun report(
        token: String,
        targetType: String,
        targetId: Long,
        type: String,
        reason: String,
    ): ResultActions = mockMvc.perform(
        post("/reports")
            .header(AUTH, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload(targetType, targetId, type, reason)),
    )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
