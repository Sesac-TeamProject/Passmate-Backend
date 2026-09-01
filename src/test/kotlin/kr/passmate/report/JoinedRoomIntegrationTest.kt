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
 * 참여한 방 목록. 끝난 방·진행 전 방을 섞어 두고 성적·리포트 여부·페이징을 본다.
 */
@AutoConfigureMockMvc
@Transactional
class JoinedRoomIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var hostId: Long = 0
    private var studentId: Long = 0
    private var setId: Long = 0
    private var questionId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("join-host", "민수쌤")
        studentId = member("join-student", "학생")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("참여 테스트"))
        setId = set.id
        questionId = questionSetService.addQuestion(
            setId, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(setId, hostId)
    }

    @Test
    fun `참여 기록이 없으면 빈 목록과 0 요약을 준다`() {
        val body = joined().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("rooms").get("content")).isEmpty()
        assertThat(body.get("rooms").get("totalElements").asLong()).isZero()
        assertThat(body.get("rooms").get("hasNext").asBoolean()).isFalse()
        assertThat(body.get("summary").get("completedSessionCount").asInt()).isZero()
    }

    @Test
    fun `끝난 방은 점수·순위·리포트 여부가 채워진다`() {
        playRoom("1교시")

        val room = joined().andExpect(status().isOk).andReturn().json().get("rooms").get("content").single()

        assertThat(room.get("title").asText()).isEqualTo("1교시")
        assertThat(room.get("hostNickname").asText()).isEqualTo("민수쌤")
        assertThat(room.get("questionCount").asInt()).isEqualTo(1)
        assertThat(room.get("myScore").asInt()).isGreaterThan(100)
        assertThat(room.get("myRank").asInt()).isEqualTo(1)
        assertThat(room.get("myAccuracy").asDouble()).isEqualTo(100.0)
        assertThat(room.get("hasReport").asBoolean()).isTrue()
    }

    @Test
    fun `아직 안 끝난 방은 성적 없이 목록에만 나온다`() {
        val room = roomService.create(hostId, RoomCreateRequest(title = "예정", type = RoomType.FREE))
        participantService.join(room.id, studentId, JoinRoomRequest(nickname = "학생"))

        val row = joined().andExpect(status().isOk).andReturn().json().get("rooms").get("content").single()

        assertThat(row.get("status").asText()).isEqualTo("WAITING")
        assertThat(row.get("hasReport").asBoolean()).isFalse()
        // non_null 직렬화라 아직 없는 성적은 필드째 빠진다
        assertThat(row.has("myScore")).isFalse()
    }

    @Test
    fun `최근 참여한 방이 먼저 온다`() {
        playRoom("1교시")
        playRoom("2교시")

        val titles = joined().andExpect(status().isOk).andReturn().json()
            .get("rooms").get("content").map { it.get("title").asText() }

        assertThat(titles).containsExactly("2교시", "1교시")
    }

    @Test
    fun `페이지를 나눠서 준다`() {
        playRoom("1교시")
        playRoom("2교시")
        playRoom("3교시")

        val first = joined(page = 0, size = 2).andExpect(status().isOk).andReturn().json().get("rooms")
        assertThat(first.get("content")).hasSize(2)
        assertThat(first.get("totalElements").asLong()).isEqualTo(3)
        assertThat(first.get("totalPages").asInt()).isEqualTo(2)
        assertThat(first.get("hasNext").asBoolean()).isTrue()

        val second = joined(page = 1, size = 2).andExpect(status().isOk).andReturn().json().get("rooms")
        assertThat(second.get("content")).hasSize(1)
        assertThat(second.get("hasNext").asBoolean()).isFalse()
    }

    @Test
    fun `요약은 페이지와 무관하게 전체 기록으로 낸다`() {
        playRoom("1교시")
        playRoom("2교시")

        joined(page = 1, size = 1)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rooms.content.length()").value(1))
            // 한 줄만 보여 줘도 요약은 두 세션 전부를 센다
            .andExpect(jsonPath("$.summary.completedSessionCount").value(2))
            .andExpect(jsonPath("$.summary.averageAccuracy").value(100.0))
    }

    // ---------- helpers ----------

    private fun playRoom(title: String) {
        val room = roomService.create(hostId, RoomCreateRequest(title = title, type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = title, questionSetId = setId))
        participantService.join(room.id, studentId, JoinRoomRequest(nickname = "학생"))

        host(post("/rooms/{id}/session/start", room.id)).andExpect(status().isNoContent)
        mockMvc.perform(
            post("/rooms/{id}/session/questions/{q}/answers", room.id, questionId)
                .header(AUTH, "Bearer $studentToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("submitted" to "찾을 수 없음"))),
        ).andExpect(status().isCreated)
        host(post("/rooms/{id}/session/end", room.id)).andExpect(status().isNoContent)
    }

    private fun host(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
        mockMvc.perform(builder.header(AUTH, "Bearer $hostToken"))

    private fun joined(page: Int? = null, size: Int? = null): ResultActions {
        val request = get("/users/me/rooms/joined").header(AUTH, "Bearer $studentToken")
        page?.let { request.param("page", it.toString()) }
        size?.let { request.param("size", it.toString()) }
        return mockMvc.perform(request)
    }

    private fun member(key: String, name: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", name, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
