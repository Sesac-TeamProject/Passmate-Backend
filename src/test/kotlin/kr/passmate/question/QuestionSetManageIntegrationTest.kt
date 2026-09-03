package kr.passmate.question

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionSetStatus
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.repository.QuestionSetRepository
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 문제 세트 복제·삭제. 확정된 세트(객관식 1 + 서술형 1)를 깔아 놓고 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class QuestionSetManageIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var questionSetRepository: QuestionSetRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var ownerId: Long = 0
    private var setId: Long = 0
    private lateinit var ownerToken: String
    private lateinit var strangerToken: String

    @BeforeEach
    fun setUp() {
        ownerId = member("set-owner")
        ownerToken = jwtTokenProvider.issue(ownerId, false).accessToken
        strangerToken = jwtTokenProvider.issue(member("set-stranger"), false).accessToken

        val set = questionSetService.create(ownerId, QuestionSetCreateRequest("네트워크 기초", "1주차"))
        questionSetService.addQuestion(
            set.id, ownerId,
            QuestionRequest(
                QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음",
                explanation = "Not Found", topic = "HTTP", difficulty = Difficulty.EASY,
                timeLimitSec = 30, points = 100,
            ),
        )
        questionSetService.addQuestion(
            set.id, ownerId,
            QuestionRequest(QuestionType.ESSAY, "TCP 를 설명하시오", answer = "연결지향", timeLimitSec = 60, points = 200),
        )
        questionSetService.confirm(set.id, ownerId)
        setId = set.id
    }

    @Test
    fun `확정 세트를 복제하면 DRAFT 사본이 생긴다`() {
        val body = duplicate(ownerToken).andExpect(status().isCreated).andReturn().json()

        assertThat(body.get("id").asLong()).isNotEqualTo(setId)
        assertThat(body.get("title").asText()).isEqualTo("네트워크 기초 (복사본)")
        assertThat(body.get("description").asText()).isEqualTo("1주차")
        assertThat(body.get("status").asText()).isEqualTo("DRAFT")
        assertThat(body.get("questionCount").asInt()).isEqualTo(2)
        assertThat(body.get("totalPoints").asInt()).isEqualTo(300)
        // 사본은 아직 쓰인 적이 없다
        assertThat(body.get("usageCount").asInt()).isZero()
        assertThat(body.has("confirmedAt")).isFalse()
    }

    @Test
    fun `사본 제목을 직접 정할 수 있다`() {
        duplicate(ownerToken, title = "네트워크 기초 2주차")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("네트워크 기초 2주차"))
    }

    @Test
    fun `문항이 정답·해설·주제까지 그대로 옮겨진다`() {
        val copyId = duplicate(ownerToken).andReturn().json().get("id").asLong()

        val detail = mockMvc.perform(get("/question-sets/{id}", copyId).header(AUTH, "Bearer $ownerToken"))
            .andExpect(status().isOk).andReturn().json()

        val questions = detail.get("questions")
        assertThat(questions).hasSize(2)
        assertThat(questions.map { it.get("orderNo").asInt() }).containsExactly(1, 2)

        val mcq = questions[0]
        assertThat(mcq.get("content").asText()).isEqualTo("404 는?")
        assertThat(mcq.get("answer").asText()).isEqualTo("찾을 수 없음")
        assertThat(mcq.get("explanation").asText()).isEqualTo("Not Found")
        assertThat(mcq.get("topic").asText()).isEqualTo("HTTP")
        assertThat(mcq.get("choices").map { it.asText() }).containsExactly("성공", "찾을 수 없음")
        assertThat(questions[1].get("type").asText()).isEqualTo("ESSAY")
    }

    @Test
    fun `사본은 확정 전이라 다시 고칠 수 있다`() {
        val copyId = duplicate(ownerToken).andReturn().json().get("id").asLong()

        mockMvc.perform(
            post("/question-sets/{id}/questions", copyId)
                .header(AUTH, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("type" to "OX", "content" to "HTTP 는 무상태다", "answer" to "O", "timeLimitSec" to 20, "points" to 50),
                    ),
                ),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `원본은 복제 뒤에도 확정 상태 그대로다`() {
        duplicate(ownerToken).andExpect(status().isCreated)

        mockMvc.perform(get("/question-sets/{id}", setId).header(AUTH, "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.set.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.questions.length()").value(2))
    }

    @Test
    fun `제목이 상한에 붙어 있어도 복사본 표시가 잘리지 않는다`() {
        val longTitle = "가".repeat(100)
        val set = questionSetService.create(ownerId, QuestionSetCreateRequest(longTitle))
        questionSetService.addQuestion(
            set.id, ownerId,
            QuestionRequest(QuestionType.OX, "참인가", answer = "O", timeLimitSec = 20, points = 50),
        )

        val title = duplicate(ownerToken, setId = set.id).andExpect(status().isCreated)
            .andReturn().json().get("title").asText()

        assertThat(title).hasSizeLessThanOrEqualTo(100)
        assertThat(title).endsWith(" (복사본)")
    }

    @Test
    fun `남의 세트는 복제할 수 없다`() {
        duplicate(strangerToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_QUESTION_SET_OWNER"))
    }

    @Test
    fun `삭제하면 목록과 조회에서 사라진다`() {
        remove(ownerToken).andExpect(status().isNoContent)

        mockMvc.perform(get("/question-sets/{id}", setId).header(AUTH, "Bearer $ownerToken"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_NOT_FOUND"))

        val list = mockMvc.perform(get("/question-sets").header(AUTH, "Bearer $ownerToken"))
            .andExpect(status().isOk).andReturn().json()
        assertThat(list.get("content").map { it.get("id").asLong() }).doesNotContain(setId)
    }

    @Test
    fun `삭제는 감추기만 한다 — 지난 세션의 출제 근거라 행은 남는다`() {
        remove(ownerToken).andExpect(status().isNoContent)

        val row = questionSetRepository.findById(setId)
        assertThat(row).isPresent()
        assertThat(row.get().deletedAt).isNotNull()
    }

    @Test
    fun `아직 안 끝난 방이 쓰고 있으면 삭제를 막는다`() {
        val room = roomService.create(ownerId, RoomCreateRequest(title = "예정된 방", type = RoomType.FREE))
        roomService.update(room.id, ownerId, RoomUpdateRequest(title = "예정된 방", questionSetId = setId))

        remove(ownerToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    @Test
    fun `끝난 방이 쓴 세트는 삭제할 수 있다`() {
        val room = roomService.create(ownerId, RoomCreateRequest(title = "끝난 방", type = RoomType.FREE))
        roomService.update(room.id, ownerId, RoomUpdateRequest(title = "끝난 방", questionSetId = setId))
        roomService.close(room.id, ownerId)

        remove(ownerToken).andExpect(status().isNoContent)
    }

    @Test
    fun `남의 세트는 삭제할 수 없다`() {
        remove(strangerToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_QUESTION_SET_OWNER"))
    }

    @Test
    fun `이미 지운 세트는 다시 지울 수 없다`() {
        remove(ownerToken).andExpect(status().isNoContent)

        remove(ownerToken)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_NOT_FOUND"))
    }

    // ---------- helpers ----------

    private fun duplicate(token: String, setId: Long = this.setId, title: String? = null): ResultActions {
        val request = post("/question-sets/{id}/duplicate", setId).header(AUTH, "Bearer $token")
        title?.let {
            request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to it)))
        }
        return mockMvc.perform(request)
    }

    private fun remove(token: String): ResultActions =
        mockMvc.perform(delete("/question-sets/{id}", setId).header(AUTH, "Bearer $token"))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
