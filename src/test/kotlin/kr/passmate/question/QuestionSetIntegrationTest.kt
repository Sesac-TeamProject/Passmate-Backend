package kr.passmate.question

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class QuestionSetIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var ownerToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setUp() {
        ownerToken = tokenFor("owner")
        otherToken = tokenFor("other")
    }

    @Test
    fun `빈 세트를 만들고 문항을 채운 뒤 확정한다`() {
        val setId = createSet()

        addQuestion(setId, mcq("HTTP 상태 코드 404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderNo").value(1))
        addQuestion(setId, """{"type":"OX","content":"TCP 는 연결지향이다","answer":"O"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.orderNo").value(2))

        mockMvc.perform(get("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.set.questionCount").value(2))
            .andExpect(jsonPath("$.set.totalPoints").value(200))
            .andExpect(jsonPath("$.set.estimatedSeconds").value(60))
            .andExpect(jsonPath("$.set.source").value("MANUAL"))
            .andExpect(jsonPath("$.questions.length()").value(2))
            .andExpect(jsonPath("$.questions[0].choices.length()").value(2))

        mockMvc.perform(post("/question-sets/{id}/confirm", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.confirmedAt").isNotEmpty)
    }

    @Test
    fun `문항이 없으면 확정할 수 없다`() {
        val setId = createSet()

        mockMvc.perform(post("/question-sets/{id}/confirm", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_EMPTY"))
    }

    @Test
    fun `확정된 세트는 수정도 문항 추가도 막는다`() {
        val setId = confirmedSet()

        mockMvc.perform(
            put("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"바꾼 제목"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_ALREADY_CONFIRMED"))

        addQuestion(setId, """{"type":"OX","content":"추가 시도","answer":"X"}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_ALREADY_CONFIRMED"))
    }

    @Test
    fun `남의 세트는 보지도 고치지도 못한다`() {
        val setId = createSet()

        mockMvc.perform(get("/question-sets/{id}", setId).header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_QUESTION_SET_OWNER"))
    }

    @Test
    fun `문항 순서를 바꾼다`() {
        val setId = createSet()
        val first = addQuestionId(setId, """{"type":"OX","content":"첫번째","answer":"O"}""")
        val second = addQuestionId(setId, """{"type":"OX","content":"두번째","answer":"O"}""")
        val third = addQuestionId(setId, """{"type":"OX","content":"세번째","answer":"O"}""")

        mockMvc.perform(
            put("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"CS 면접","questionOrder":[$third,$first,$second]}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(jsonPath("$.questions[0].content").value("세번째"))
            .andExpect(jsonPath("$.questions[1].content").value("첫번째"))
            .andExpect(jsonPath("$.questions[2].content").value("두번째"))
    }

    @Test
    fun `순서 목록에 문항이 빠지면 거부한다`() {
        val setId = createSet()
        val first = addQuestionId(setId, """{"type":"OX","content":"첫번째","answer":"O"}""")
        addQuestionId(setId, """{"type":"OX","content":"두번째","answer":"O"}""")

        mockMvc.perform(
            put("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"CS 면접","questionOrder":[$first]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `문항을 지우면 순서를 다시 매기고 집계도 맞춘다`() {
        val setId = createSet()
        val first = addQuestionId(setId, """{"type":"OX","content":"첫번째","answer":"O"}""")
        addQuestionId(setId, """{"type":"OX","content":"두번째","answer":"O"}""")
        addQuestionId(setId, """{"type":"OX","content":"세번째","answer":"O"}""")

        mockMvc.perform(
            delete("/question-sets/{id}/questions/{qid}", setId, first)
                .header("Authorization", "Bearer $ownerToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(jsonPath("$.set.questionCount").value(2))
            .andExpect(jsonPath("$.set.totalPoints").value(200))
            .andExpect(jsonPath("$.questions[0].orderNo").value(1))
            .andExpect(jsonPath("$.questions[0].content").value("두번째"))
            .andExpect(jsonPath("$.questions[1].orderNo").value(2))
    }

    @Test
    fun `잘못된 문항은 400 으로 막는다`() {
        val setId = createSet()

        // 정답이 보기에 없다
        addQuestion(setId, mcq("보기 밖 정답", listOf("가", "나"), "다"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_QUESTION"))
    }

    @Test
    fun `목록은 내 세트만 나오고 상태로 거를 수 있다`() {
        createSet("초안 세트")
        confirmedSet()

        mockMvc.perform(get("/question-sets").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))

        mockMvc.perform(
            get("/question-sets").param("status", "CONFIRMED")
                .header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))

        mockMvc.perform(get("/question-sets").header("Authorization", "Bearer $otherToken"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    // ---------- 문항 수정·삭제 ----------

    @Test
    fun `문항을 수정하면 내용이 바뀌고 세트 집계도 다시 계산된다`() {
        val setId = createSet()
        val questionId = addQuestionId(setId, """{"type":"OX","content":"TCP 는 연결지향이다","answer":"O"}""")

        mockMvc.perform(
            put("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"type":"MCQ","content":"연결지향 프로토콜은?","choices":["TCP","UDP"],
                       "answer":"TCP","points":200,"timeLimitSec":60}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(questionId))
            .andExpect(jsonPath("$.type").value("MCQ"))
            .andExpect(jsonPath("$.content").value("연결지향 프로토콜은?"))
            .andExpect(jsonPath("$.choices.length()").value(2))
            .andExpect(jsonPath("$.points").value(200))
            .andExpect(jsonPath("$.timeLimitSec").value(60))
            .andExpect(jsonPath("$.orderNo").value(1))

        mockMvc.perform(get("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.set.totalPoints").value(200))
            .andExpect(jsonPath("$.set.estimatedSeconds").value(60))
    }

    @Test
    fun `객관식으로 수정할 때 정답이 보기에 없으면 400 이다`() {
        val setId = createSet()
        val questionId = addQuestionId(setId, """{"type":"OX","content":"수정 대상","answer":"O"}""")

        mockMvc.perform(
            put("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"MCQ","content":"보기 밖 정답","choices":["A","B"],"answer":"C"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_QUESTION"))
    }

    @Test
    fun `남의 세트 문항은 수정도 삭제도 403 이다`() {
        val setId = createSet()
        val questionId = addQuestionId(setId, """{"type":"OX","content":"남의 문항","answer":"O"}""")

        mockMvc.perform(
            put("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"OX","content":"가로채기","answer":"X"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_QUESTION_SET_OWNER"))

        mockMvc.perform(
            delete("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $otherToken"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_QUESTION_SET_OWNER"))
    }

    @Test
    fun `다른 세트의 문항 id 로는 수정할 수 없다`() {
        val setId = createSet("세트 A")
        val otherSetId = createSet("세트 B")
        val questionId = addQuestionId(otherSetId, """{"type":"OX","content":"B 의 문항","answer":"O"}""")

        mockMvc.perform(
            put("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"OX","content":"바꿔치기","answer":"X"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))

        mockMvc.perform(
            delete("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))
    }

    @Test
    fun `확정된 세트의 문항은 수정도 삭제도 409 다`() {
        val setId = createSet("확정 전 세트")
        val questionId = addQuestionId(setId, """{"type":"OX","content":"확정될 문항","answer":"O"}""")
        mockMvc.perform(post("/question-sets/{id}/confirm", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)

        mockMvc.perform(
            put("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"OX","content":"뒤늦은 수정","answer":"X"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_ALREADY_CONFIRMED"))

        mockMvc.perform(
            delete("/question-sets/{id}/questions/{qid}", setId, questionId)
                .header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_ALREADY_CONFIRMED"))
    }

    // ---------- helpers ----------

    private fun tokenFor(key: String): String {
        val outcome = userService.loginOrRegister(
            AuthProvider.GOOGLE, "qs-$key", "$key@example.com", key, null,
        )
        return jwtTokenProvider.issue(outcome.user.id, outcome.user.isAdmin).accessToken
    }

    private fun createSet(title: String = "CS 면접"): Long =
        mockMvc.perform(
            post("/question-sets").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"$title"}"""),
        ).andExpect(status().isCreated).andReturn().json().get("id").asLong()

    private fun confirmedSet(): Long {
        val setId = createSet("확정 세트")
        addQuestion(setId, """{"type":"OX","content":"확정용","answer":"O"}""")
        mockMvc.perform(post("/question-sets/{id}/confirm", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
        return setId
    }

    private fun addQuestion(setId: Long, body: String) = mockMvc.perform(
        post("/question-sets/{id}/questions", setId).header("Authorization", "Bearer $ownerToken")
            .contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun addQuestionId(setId: Long, body: String): Long =
        addQuestion(setId, body).andExpect(status().isCreated).andReturn().json().get("id").asLong()

    private fun mcq(content: String, choices: List<String>, answer: String) =
        """{"type":"MCQ","content":"$content","choices":${objectMapper.writeValueAsString(choices)},"answer":"$answer"}"""

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
