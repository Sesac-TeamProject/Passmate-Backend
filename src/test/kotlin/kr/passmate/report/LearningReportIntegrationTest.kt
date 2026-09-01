package kr.passmate.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.report.repository.ParticipantReportRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 개인 학습 리포트.
 *
 * 문항 두 개에 각각 다른 주제를 달아 두고, 학생이 하나만 맞히게 해서
 * 취약 주제가 제대로 걸러지는지 본다.
 */
@AutoConfigureMockMvc
@Transactional
class LearningReportIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var reportRepository: ParticipantReportRepository

    private var hostId: Long = 0
    private var roomId: Long = 0
    private var firstId: Long = 0
    private var secondId: Long = 0
    private var studentParticipantId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String
    private lateinit var guestToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("rep-host")
        val studentId = member("rep-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("리포트 테스트"))
        firstId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(
                QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음",
                topic = "HTTP", timeLimitSec = 30, points = 100,
            ),
        ).id
        secondId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(
                QuestionType.MCQ, "TCP 는 몇 계층?", listOf("전송", "응용"), "전송",
                topic = "네트워크 계층", timeLimitSec = 30, points = 100,
            ),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "리포트 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "리포트 방", questionSetId = set.id))
        roomId = room.id

        studentParticipantId = participantService.join(roomId, studentId, JoinRoomRequest(nickname = "학생")).participant.id
        guestToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트")).accessToken!!
    }

    @Test
    fun `세션이 끝나면 리포트가 자동으로 만들어진다`() {
        runSession()

        val body = myReport(studentToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("totalQuestions").asInt()).isEqualTo(2)
        assertThat(body.get("correctCount").asInt()).isEqualTo(1)
        assertThat(body.get("accuracy").asDouble()).isEqualTo(50.0)
        assertThat(body.get("finalRank").asInt()).isEqualTo(1)
        assertThat(body.get("generatedAt").isNull).isFalse()

        // 종료 이벤트가 참가자 전원의 리포트를 찍는다
        assertThat(reportRepository.findByParticipantId(studentParticipantId)).isNotNull
    }

    @Test
    fun `틀린 문항의 주제만 취약 주제로 남는다`() {
        runSession()

        myReport(studentToken)
            .andExpect(status().isOk)
            // 1번(HTTP)은 맞혔고 2번(네트워크 계층)은 틀렸다
            .andExpect(jsonPath("$.weakTopics.length()").value(1))
            .andExpect(jsonPath("$.weakTopics[0]").value("네트워크 계층"))
            .andExpect(jsonPath("$.improvementPoints[0]").value("네트워크 계층 주제를 다시 살펴보세요."))
    }

    @Test
    fun `세션이 끝나기 전에는 리포트가 없다`() {
        start()

        myReport(studentToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_NOT_RUNNING"))
    }

    @Test
    fun `한 문제도 안 푼 게스트도 자기 리포트는 본다`() {
        runSession()

        myReport(guestToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("게스트"))
            .andExpect(jsonPath("$.correctCount").value(0))
            .andExpect(jsonPath("$.accuracy").value(0.0))
            // 미제출도 못 맞힌 것으로 세므로 두 주제가 모두 약점이 된다
            .andExpect(jsonPath("$.weakTopics.length()").value(2))
    }

    // ---------- helpers ----------

    /** 학생이 1번은 맞히고 2번은 틀린다. 게스트는 아무것도 내지 않는다. */
    private fun runSession() {
        start()
        submit(studentToken, firstId, "찾을 수 없음")
        endCurrent()
        next()
        submit(studentToken, secondId, "응용")
        endSession()
    }

    private fun start() = mockMvc.perform(post("/rooms/{id}/session/start", roomId).header(AUTH, bearer(hostToken)))
        .andExpect(status().isNoContent)

    private fun next() = mockMvc.perform(post("/rooms/{id}/session/next", roomId).header(AUTH, bearer(hostToken)))
        .andExpect(status().isNoContent)

    private fun endCurrent() = mockMvc.perform(post("/rooms/{id}/session/current/end", roomId).header(AUTH, bearer(hostToken)))
        .andExpect(status().isNoContent)

    private fun endSession() = mockMvc.perform(post("/rooms/{id}/session/end", roomId).header(AUTH, bearer(hostToken)))
        .andExpect(status().isNoContent)

    private fun submit(token: String, questionId: Long, submitted: String) = mockMvc.perform(
        post("/rooms/{id}/session/questions/{q}/answers", roomId, questionId)
            .header(AUTH, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("submitted" to submitted))),
    ).andExpect(status().isCreated)

    private fun myReport(token: String): ResultActions =
        mockMvc.perform(get("/rooms/{id}/reports/me", roomId).header(AUTH, bearer(token)))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun bearer(token: String) = "Bearer $token"

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
