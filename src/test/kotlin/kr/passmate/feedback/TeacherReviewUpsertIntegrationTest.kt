package kr.passmate.feedback

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.common.security.UserPrincipal
import kr.passmate.feedback.repository.TeacherReviewRepository
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.repository.RoomRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 첨삭 등록·수정. 객관식(100점) + 서술형(200점) 방을 한 바퀴 돌린다.
 *
 * 서술형은 제출만 하면 배점을 잠정으로 받으므로(FR-024), 보정으로 점수를 깎으면
 * 등수가 실제로 뒤집힌다 — 그 파급까지 확인한다.
 *
 * 처음 점수: 민수 = 100+보너스+200, 지은 = 0+200
 */
@AutoConfigureMockMvc
@Transactional
class TeacherReviewUpsertIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var answerQueryService: AnswerQueryService
    @Autowired private lateinit var teacherReviewRepository: TeacherReviewRepository
    @Autowired private lateinit var roomRepository: RoomRepository

    private var hostId: Long = 0
    private var minsuId: Long = 0
    private var jieunId: Long = 0
    private var roomId: Long = 0
    private var mcqId: Long = 0
    private var essayId: Long = 0
    private var minsuEssayAnswerId: Long = 0
    private lateinit var hostToken: String
    private lateinit var minsuToken: String
    private lateinit var jieunToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("upsert-host")
        minsuId = member("upsert-minsu")
        jieunId = member("upsert-jieun")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        minsuToken = jwtTokenProvider.issue(minsuId, false).accessToken
        jieunToken = jwtTokenProvider.issue(jieunId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("첨삭 수정 테스트"))
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

        participantService.join(roomId, minsuId, JoinRoomRequest(nickname = "민수"))
        participantService.join(roomId, jieunId, JoinRoomRequest(nickname = "지은"))

        runSession()
        minsuEssayAnswerId = answerQueryService.getMyAnswer(roomId, essayId, UserPrincipal(minsuId, false)).id
    }

    @Test
    fun `첨삭하면 보정 점수가 최종 점수가 된다`() {
        val body = review(hostToken, minsuEssayAnswerId, comment = "핵심은 짚었습니다", adjustedScore = 120, improvement = "3-way handshake 를 덧붙이세요")
            .andExpect(status().isOk).andReturn().json()

        assertThat(body.get("answerId").asLong()).isEqualTo(minsuEssayAnswerId)
        assertThat(body.get("finalScore").asInt()).isEqualTo(120)
        assertThat(body.get("review").get("comment").asText()).isEqualTo("핵심은 짚었습니다")
        assertThat(body.get("review").get("adjustedScore").asInt()).isEqualTo(120)
        assertThat(body.get("review").get("improvement").asText()).isEqualTo("3-way handshake 를 덧붙이세요")
    }

    @Test
    fun `다시 첨삭하면 행이 늘지 않고 덮어쓴다`() {
        review(hostToken, minsuEssayAnswerId, comment = "처음", adjustedScore = 120).andExpect(status().isOk)
        review(hostToken, minsuEssayAnswerId, comment = "다시 봤습니다", adjustedScore = 160).andExpect(status().isOk)

        assertThat(teacherReviewRepository.findAll().filter { it.answerId == minsuEssayAnswerId }).hasSize(1)
        val review = teacherReviewRepository.findByAnswerId(minsuEssayAnswerId)!!
        assertThat(review.comment).isEqualTo("다시 봤습니다")
        assertThat(review.adjustedScore).isEqualTo(160)
    }

    @Test
    fun `보정을 지우면 채점기가 낸 잠정 점수로 되돌아간다`() {
        review(hostToken, minsuEssayAnswerId, adjustedScore = 20).andExpect(status().isOk)

        val body = review(hostToken, minsuEssayAnswerId, comment = "다시 보니 괜찮습니다")
            .andExpect(status().isOk).andReturn().json()

        // 서술형은 제출만 해도 배점을 잠정으로 받는다
        assertThat(body.get("finalScore").asInt()).isEqualTo(200)
        assertThat(body.get("review").has("adjustedScore")).isFalse()
    }

    @Test
    fun `코멘트만 달면 점수는 그대로다`() {
        review(hostToken, minsuEssayAnswerId, comment = "잘 썼습니다")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.finalScore").value(200))
    }

    @Test
    fun `보정으로 등수가 뒤집히고 학습 리포트도 다시 찍힌다`() {
        // 처음엔 민수가 1등(객관식 정답 + 서술형 200)
        assertThat(myReport(minsuToken).get("finalRank").asInt()).isEqualTo(1)
        assertThat(myReport(jieunToken).get("finalRank").asInt()).isEqualTo(2)

        // 민수 서술형을 0점으로 깎으면 지은(200점)이 앞선다
        review(hostToken, minsuEssayAnswerId, adjustedScore = 0).andExpect(status().isOk)

        assertThat(myReport(minsuToken).get("finalRank").asInt()).isEqualTo(2)
        assertThat(myReport(jieunToken).get("finalRank").asInt()).isEqualTo(1)
        assertThat(myReport(jieunToken).get("totalScore").asInt()).isEqualTo(200)
    }

    @Test
    fun `보정하면 방 평균 점수도 다시 계산된다`() {
        val before = roomRepository.findById(roomId).get().avgScore!!

        review(hostToken, minsuEssayAnswerId, adjustedScore = 0).andExpect(status().isOk)

        val after = roomRepository.findById(roomId).get().avgScore!!
        // 200점이 빠졌으니 2명 평균으로 100점 낮아진다
        assertThat(before.subtract(after).toInt()).isEqualTo(100)
    }

    @Test
    fun `객관식은 점수를 보정할 수 없다`() {
        val mcqAnswerId = answerQueryService.getMyAnswer(roomId, mcqId, UserPrincipal(minsuId, false)).id

        review(hostToken, mcqAnswerId, adjustedScore = 50)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `객관식에도 코멘트는 달 수 있다`() {
        val mcqAnswerId = answerQueryService.getMyAnswer(roomId, mcqId, UserPrincipal(minsuId, false)).id

        review(hostToken, mcqAnswerId, comment = "잘 골랐습니다").andExpect(status().isOk)
    }

    @Test
    fun `배점을 넘는 보정은 거절한다`() {
        review(hostToken, minsuEssayAnswerId, adjustedScore = 201)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `음수 보정은 거절한다`() {
        review(hostToken, minsuEssayAnswerId, adjustedScore = -1).andExpect(status().isBadRequest)
    }

    @Test
    fun `학생은 첨삭할 수 없다`() {
        review(minsuToken, minsuEssayAnswerId, comment = "제가 매기겠습니다")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `다른 방 답안 id 로는 첨삭할 수 없다`() {
        val other = roomService.create(hostId, RoomCreateRequest(title = "다른 방", type = RoomType.FREE))

        mockMvc.perform(
            put("/rooms/{id}/answers/{a}/review", other.id, minsuEssayAnswerId)
                .header(AUTH, "Bearer $hostToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("comment" to "남의 방"))),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    // ---------- helpers ----------

    /** 민수는 객관식 정답, 지은은 오답. 서술형은 둘 다 제출. */
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

    private fun review(
        token: String,
        answerId: Long,
        comment: String? = null,
        adjustedScore: Int? = null,
        improvement: String? = null,
    ): ResultActions {
        val payload = buildMap<String, Any> {
            comment?.let { put("comment", it) }
            adjustedScore?.let { put("adjustedScore", it) }
            improvement?.let { put("improvement", it) }
        }
        return mockMvc.perform(
            put("/rooms/{id}/answers/{a}/review", roomId, answerId)
                .header(AUTH, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)),
        )
    }

    private fun myReport(token: String): JsonNode =
        mockMvc.perform(get("/rooms/{id}/reports/me", roomId).header(AUTH, "Bearer $token"))
            .andExpect(status().isOk).andReturn().json()

    private fun perform(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String) =
        mockMvc.perform(builder.header(AUTH, "Bearer $token"))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
