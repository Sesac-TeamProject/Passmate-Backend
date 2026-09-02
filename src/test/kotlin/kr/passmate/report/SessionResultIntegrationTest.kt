package kr.passmate.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.common.security.UserPrincipal
import kr.passmate.feedback.domain.TeacherReview
import kr.passmate.feedback.repository.TeacherReviewRepository
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
import kr.passmate.session.service.AnswerQueryService
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
 * 세션 결과 3건. 객관식 1 + 서술형 1 짜리 방을 한 바퀴 돌려 놓고 확인한다.
 *
 * 참가자는 셋 — 둘은 답을 내고 하나는 아무것도 내지 않는다(미제출 경로용).
 */
@AutoConfigureMockMvc
@Transactional
class SessionResultIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var answerQueryService: AnswerQueryService
    @Autowired private lateinit var teacherReviewRepository: TeacherReviewRepository

    private var hostId: Long = 0
    private var studentId: Long = 0
    private var roomId: Long = 0
    private var mcqId: Long = 0
    private var essayId: Long = 0
    private var studentParticipantId: Long = 0
    private var idleParticipantId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String
    private lateinit var guestToken: String
    private lateinit var idleToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("res-host")
        studentId = member("res-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("결과 테스트"))
        mcqId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", explanation = "Not Found", timeLimitSec = 30, points = 100),
        ).id
        essayId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.ESSAY, "TCP 를 설명하시오", answer = "연결지향", timeLimitSec = 60, points = 200),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "결과 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "결과 방", questionSetId = set.id))
        roomId = room.id

        studentParticipantId = participantService.join(roomId, studentId, JoinRoomRequest(nickname = "학생")).participant.id
        guestToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트")).accessToken!!
        val idle = participantService.join(roomId, null, JoinRoomRequest(nickname = "구경꾼"))
        idleParticipantId = idle.participant.id
        idleToken = idle.accessToken!!
    }

    @Test
    fun `호스트는 요약·문항별·학생별을 한 번에 본다`() {
        runWholeSession()

        val body = results(hostToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("summary").get("participantCount").asInt()).isEqualTo(3)
        assertThat(body.get("summary").get("questionCount").asInt()).isEqualTo(2)
        // 채점된 답안은 객관식 2건(정답 1·오답 1). 서술형은 채점 전이라 빠진다
        assertThat(body.get("summary").get("avgCorrectRate").asDouble()).isEqualTo(50.0)

        val questions = body.get("questions")
        assertThat(questions).hasSize(2)
        assertThat(questions[0].get("orderNo").asInt()).isEqualTo(1)
        assertThat(questions[0].get("submitCount").asInt()).isEqualTo(2)
        assertThat(questions[0].get("correctCount").asInt()).isEqualTo(1)
        assertThat(questions[0].get("correctRate").asDouble()).isEqualTo(50.0)

        val participants = body.get("participants")
        assertThat(participants).hasSize(3)
        assertThat(participants[0].get("rank").asInt()).isEqualTo(1)
        // 아무것도 내지 않은 참가자도 0점으로 줄에 남는다
        assertThat(participants.last().get("totalScore").asLong()).isZero()
        assertThat(participants.last().get("submitCount").asInt()).isZero()
    }

    @Test
    fun `학생은 방 전체 통계를 볼 수 없다`() {
        runWholeSession()

        results(studentToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `내 결과에는 미제출 문항도 줄이 남는다`() {
        start()
        submit(studentToken, mcqId, "찾을 수 없음")
        endSession()

        val body = myResult(studentToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("questionCount").asInt()).isEqualTo(2)
        assertThat(body.get("submitCount").asInt()).isEqualTo(1)
        assertThat(body.get("questions")).hasSize(2)

        val essay = body.get("questions")[1]
        // non_null 직렬화라 미제출은 필드 자체가 빠진다
        assertThat(essay.has("submitted")).isFalse()
        assertThat(essay.get("score").asInt()).isZero()
        assertThat(essay.get("analysisStatus").asText()).isEqualTo("NOT_REQUESTED")
    }

    @Test
    fun `게스트도 자기 결과는 보고 가입 유도 표시가 붙는다`() {
        runWholeSession()

        myResult(guestToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.guest").value(true))
            .andExpect(jsonPath("$.nickname").value("게스트"))
    }

    @Test
    fun `진행 중에는 정답이 나가지 않고 마감 뒤에 나온다`() {
        start()
        submit(studentToken, mcqId, "찾을 수 없음")

        myResult(studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].answer").doesNotExist())
            .andExpect(jsonPath("$.questions[0].explanation").doesNotExist())

        endCurrent()

        myResult(studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].answer").value("찾을 수 없음"))
            .andExpect(jsonPath("$.questions[0].explanation").value("Not Found"))
    }

    @Test
    fun `세션이 끝나야 평가할 수 있다`() {
        start()
        submit(studentToken, mcqId, "찾을 수 없음")

        myResult(studentToken)
            .andExpect(jsonPath("$.rating.available").value(false))
            .andExpect(jsonPath("$.rating.blockedReason").value("SESSION_NOT_ENDED"))

        endSession()

        myResult(studentToken)
            .andExpect(jsonPath("$.rating.available").value(true))
            .andExpect(jsonPath("$.rating.alreadyRated").value(false))
            .andExpect(jsonPath("$.rating.deadline").isNotEmpty)
    }

    @Test
    fun `답안을 한 개도 내지 않았으면 평가할 수 없다`() {
        runWholeSession()

        myResult(idleToken)
            .andExpect(jsonPath("$.rating.available").value(false))
            .andExpect(jsonPath("$.rating.blockedReason").value("NO_SUBMISSION"))
    }

    @Test
    fun `호스트는 학생별 상세에서 첨삭까지 본다`() {
        runWholeSession()

        val answerId = answerQueryService
            .getMyAnswer(roomId, essayId, UserPrincipal(studentId, false)).id
        teacherReviewRepository.saveAndFlush(
            TeacherReview(
                answerId = answerId,
                reviewerUserId = hostId,
                comment = "핵심은 짚었습니다",
                adjustedScore = 180,
                improvement = "3-way handshake 를 덧붙이세요",
            ),
        )

        val body = participantResult(hostToken, studentParticipantId)
            .andExpect(status().isOk).andReturn().json()

        assertThat(body.get("nickname").asText()).isEqualTo("학생")
        val essay = body.get("questions")[1]
        assertThat(essay.get("teacherReview").get("comment").asText()).isEqualTo("핵심은 짚었습니다")
        assertThat(essay.get("teacherReview").get("adjustedScore").asInt()).isEqualTo(180)
        // 사람이 본 것과 기계가 본 것은 필드가 다르다 — 첨삭만 있고 AI 분석은 없다
        assertThat(essay.has("analysis")).isFalse()
        assertThat(essay.get("analysisStatus").asText()).isEqualTo("NOT_REQUESTED")
    }

    @Test
    fun `학생은 남의 결과 상세를 볼 수 없다`() {
        runWholeSession()

        participantResult(studentToken, idleParticipantId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `다른 방 참가자 id 로는 상세를 볼 수 없다`() {
        runWholeSession()
        val other = roomService.create(hostId, RoomCreateRequest(title = "다른 방", type = RoomType.FREE))
        val outsider = participantService.join(other.id, null, JoinRoomRequest(nickname = "외부인")).participant.id

        participantResult(hostToken, outsider)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_FOUND"))
    }

    // ---------- helpers ----------

    /** 객관식(학생 정답·게스트 오답) → 서술형(학생만 제출) → 종료. */
    private fun runWholeSession() {
        start()
        submit(studentToken, mcqId, "찾을 수 없음")
        submit(guestToken, mcqId, "성공")
        endCurrent()
        next()
        submit(studentToken, essayId, "연결을 맺고 주고받습니다")
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

    private fun results(token: String): ResultActions =
        mockMvc.perform(get("/rooms/{id}/results", roomId).header(AUTH, bearer(token)))

    private fun myResult(token: String): ResultActions =
        mockMvc.perform(get("/rooms/{id}/results/me", roomId).header(AUTH, bearer(token)))

    private fun participantResult(token: String, participantId: Long): ResultActions =
        mockMvc.perform(get("/rooms/{id}/results/participants/{p}", roomId, participantId).header(AUTH, bearer(token)))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun bearer(token: String) = "Bearer $token"

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
