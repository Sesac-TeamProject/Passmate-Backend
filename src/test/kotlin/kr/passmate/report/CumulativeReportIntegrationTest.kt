package kr.passmate.report

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 누적 학습 리포트. 방 두 개를 각각 다른 성적으로 끝내 놓고 평균·추이·약점을 본다.
 */
@AutoConfigureMockMvc
@Transactional
class CumulativeReportIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var hostId: Long = 0
    private var studentId: Long = 0
    private lateinit var studentToken: String
    private lateinit var hostToken: String
    private var setId: Long = 0
    private var httpQuestionId: Long = 0
    private var netQuestionId: Long = 0

    @BeforeEach
    fun setUp() {
        hostId = member("cum-host")
        studentId = member("cum-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("누적 테스트"))
        setId = set.id
        httpQuestionId = questionSetService.addQuestion(
            setId, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", topic = "HTTP", timeLimitSec = 30, points = 100),
        ).id
        netQuestionId = questionSetService.addQuestion(
            setId, hostId,
            QuestionRequest(QuestionType.MCQ, "TCP 는 몇 계층?", listOf("전송", "응용"), "전송", topic = "네트워크 계층", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(setId, hostId)
    }

    @Test
    fun `참여 기록이 없으면 0 으로 채운 리포트를 준다`() {
        report()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.joinedRoomCount").value(0))
            .andExpect(jsonPath("$.completedSessionCount").value(0))
            .andExpect(jsonPath("$.averageAccuracy").value(0.0))
            .andExpect(jsonPath("$.trend.length()").value(0))
            // 비교할 기록이 없으면 "변화 없음(0)" 이 아니라 값이 없다
            .andExpect(jsonPath("$.accuracyChangeFromLastWeek").doesNotExist())
    }

    @Test
    fun `끝난 세션들의 평균과 추이를 준다`() {
        playRoom("1교시", correctFirst = true, correctSecond = true)   // 100%
        playRoom("2교시", correctFirst = true, correctSecond = false)  // 50%

        val body = report().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("joinedRoomCount").asInt()).isEqualTo(2)
        assertThat(body.get("completedSessionCount").asInt()).isEqualTo(2)
        assertThat(body.get("averageAccuracy").asDouble()).isEqualTo(75.0)
        // 두 방 모두 혼자 참여했으므로 1등
        assertThat(body.get("averageRank").asDouble()).isEqualTo(1.0)

        val trend = body.get("trend")
        assertThat(trend).hasSize(2)
        assertThat(trend.map { it.get("roomTitle").asText() }).containsExactlyInAnyOrder("1교시", "2교시")
        assertThat(trend[0].get("playedAt").isNull).isFalse()
    }

    @Test
    fun `여러 세션에서 반복해 틀린 주제가 앞에 온다`() {
        playRoom("1교시", correctFirst = true, correctSecond = false)  // 네트워크 계층 오답
        playRoom("2교시", correctFirst = false, correctSecond = false) // HTTP·네트워크 계층 둘 다 오답

        val topics = report().andExpect(status().isOk).andReturn().json()
            .get("weakTopics").map { it.asText() }

        assertThat(topics).containsExactly("네트워크 계층", "HTTP")
    }

    @Test
    fun `한 주치 기록만 있으면 지난주 대비를 계산하지 않는다`() {
        playRoom("1교시", correctFirst = true, correctSecond = true)

        report()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.completedSessionCount").value(1))
            .andExpect(jsonPath("$.accuracyChangeFromLastWeek").doesNotExist())
    }

    // ---------- helpers ----------

    /** 방을 하나 열어 학생이 두 문항을 풀고 끝낸다. */
    private fun playRoom(title: String, correctFirst: Boolean, correctSecond: Boolean) {
        val room = roomService.create(hostId, RoomCreateRequest(title = title, type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = title, questionSetId = setId))
        participantService.join(room.id, studentId, JoinRoomRequest(nickname = "학생"))

        perform(post("/rooms/{id}/session/start", room.id)).andExpect(status().isNoContent)
        submit(room.id, httpQuestionId, if (correctFirst) "찾을 수 없음" else "성공")
        perform(post("/rooms/{id}/session/current/end", room.id)).andExpect(status().isNoContent)
        perform(post("/rooms/{id}/session/next", room.id)).andExpect(status().isNoContent)
        submit(room.id, netQuestionId, if (correctSecond) "전송" else "응용")
        perform(post("/rooms/{id}/session/end", room.id)).andExpect(status().isNoContent)
    }

    private fun submit(roomId: Long, questionId: Long, submitted: String) = mockMvc.perform(
        post("/rooms/{id}/session/questions/{q}/answers", roomId, questionId)
            .header(AUTH, "Bearer $studentToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("submitted" to submitted))),
    ).andExpect(status().isCreated)

    private fun perform(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
        mockMvc.perform(builder.header(AUTH, "Bearer $hostToken"))

    private fun report(): ResultActions =
        mockMvc.perform(get("/users/me/report").header(AUTH, "Bearer $studentToken"))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
