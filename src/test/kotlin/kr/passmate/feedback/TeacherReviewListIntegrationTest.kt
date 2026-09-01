package kr.passmate.feedback

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
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
 * 첨삭 대상 답안 목록. 문항 2개 × 학생 2명이 모두 답을 내서 4건이 나오는 판을 깔고
 * 필터·정렬·첨삭 여부를 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class TeacherReviewListIntegrationTest : IntegrationTestSupport() {

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
    private var roomId: Long = 0
    private var mcqId: Long = 0
    private var essayId: Long = 0
    private var minsuId: Long = 0
    private lateinit var hostToken: String
    private lateinit var minsuToken: String
    private lateinit var jieunToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("rev-host")
        val minsu = member("rev-minsu")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        minsuToken = jwtTokenProvider.issue(minsu, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("첨삭 테스트"))
        mcqId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        essayId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.ESSAY, "TCP 를 설명하시오", answer = "연결지향 프로토콜", timeLimitSec = 60, points = 200),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "첨삭 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "첨삭 방", questionSetId = set.id))
        roomId = room.id

        minsuId = participantService.join(roomId, minsu, JoinRoomRequest(nickname = "민수")).participant.id
        jieunToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "지은")).accessToken!!

        runSession()
    }

    @Test
    fun `호스트는 모든 답안을 문항 순서·닉네임 순으로 본다`() {
        val body = list(hostToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("totalCount").asInt()).isEqualTo(4)
        assertThat(body.get("reviewedCount").asInt()).isZero()

        val rows = body.get("answers")
        // 1번 문항의 두 명(민수·지은)이 먼저, 그 다음 2번 문항
        assertThat(rows.map { it.get("orderNo").asInt() }).containsExactly(1, 1, 2, 2)
        assertThat(rows.map { it.get("nickname").asText() }).containsExactly("민수", "지은", "민수", "지은")
    }

    @Test
    fun `첨삭 기준으로 쓰라고 모범답안을 함께 준다`() {
        list(hostToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answers[2].type").value("ESSAY"))
            .andExpect(jsonPath("$.answers[2].modelAnswer").value("연결지향 프로토콜"))
            .andExpect(jsonPath("$.answers[2].submitted").isNotEmpty)
    }

    @Test
    fun `문항으로 좁힐 수 있다`() {
        val body = list(hostToken, questionId = essayId).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("totalCount").asInt()).isEqualTo(2)
        assertThat(body.get("answers").map { it.get("questionId").asLong() }).containsOnly(essayId)
    }

    @Test
    fun `학생으로 좁힐 수 있다`() {
        val body = list(hostToken, participantId = minsuId).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("totalCount").asInt()).isEqualTo(2)
        assertThat(body.get("answers").map { it.get("nickname").asText() }).containsOnly("민수")
    }

    @Test
    fun `첨삭한 답안은 내용과 함께 완료로 표시된다`() {
        val answerId = answerQueryService.getMyAnswer(roomId, essayId, principal()).id
        teacherReviewRepository.saveAndFlush(
            TeacherReview(
                answerId = answerId,
                reviewerUserId = hostId,
                comment = "핵심은 짚었습니다",
                adjustedScore = 180,
            ),
        )

        val body = list(hostToken, participantId = minsuId).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("reviewedCount").asInt()).isEqualTo(1)
        val essay = body.get("answers").first { it.get("questionId").asLong() == essayId }
        assertThat(essay.get("reviewed").asBoolean()).isTrue()
        assertThat(essay.get("teacherReview").get("comment").asText()).isEqualTo("핵심은 짚었습니다")
        assertThat(essay.get("teacherReview").get("adjustedScore").asInt()).isEqualTo(180)
    }

    @Test
    fun `학생은 남의 답안 목록을 볼 수 없다`() {
        list(minsuToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `이 방에 없는 문항으로 거르면 빈 목록이 아니라 404 다`() {
        list(hostToken, questionId = 999_999)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))
    }

    @Test
    fun `이 방에 없는 참가자로 거르면 404 다`() {
        list(hostToken, participantId = 999_999)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_FOUND"))
    }

    // ---------- helpers ----------

    /** 두 문항 모두 두 학생이 답을 낸다 — 목록에 4건이 잡히게. */
    private fun runSession() {
        perform(post("/rooms/{id}/session/start", roomId), hostToken).andExpect(status().isNoContent)
        submit(minsuToken, mcqId, "찾을 수 없음")
        submit(jieunToken, mcqId, "성공")
        perform(post("/rooms/{id}/session/current/end", roomId), hostToken).andExpect(status().isNoContent)
        perform(post("/rooms/{id}/session/next", roomId), hostToken).andExpect(status().isNoContent)
        submit(minsuToken, essayId, "연결을 맺고 주고받습니다")
        submit(jieunToken, essayId, "패킷을 보냅니다")
        perform(post("/rooms/{id}/session/end", roomId), hostToken).andExpect(status().isNoContent)
    }

    private fun submit(token: String, questionId: Long, submitted: String) = mockMvc.perform(
        post("/rooms/{id}/session/questions/{q}/answers", roomId, questionId)
            .header(AUTH, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("submitted" to submitted))),
    ).andExpect(status().isCreated)

    private fun list(token: String, questionId: Long? = null, participantId: Long? = null): ResultActions {
        val request = get("/rooms/{id}/answers", roomId).header(AUTH, "Bearer $token")
        questionId?.let { request.param("questionId", it.toString()) }
        participantId?.let { request.param("participantId", it.toString()) }
        return mockMvc.perform(request)
    }

    private fun perform(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String) =
        mockMvc.perform(builder.header(AUTH, "Bearer $token"))

    private fun principal() = kr.passmate.common.security.UserPrincipal(
        userService.loginOrRegister(AuthProvider.GOOGLE, "rev-minsu", null, null, null).user.id,
        false,
    )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
