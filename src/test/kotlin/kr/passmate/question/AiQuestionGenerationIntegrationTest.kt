package kr.passmate.question

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.ai.client.GeneratedQuestion
import kr.passmate.ai.client.OpenAiClient
import kr.passmate.ai.domain.AiGenerationKind
import kr.passmate.ai.domain.AiGenerationStatus
import kr.passmate.ai.repository.AiGenerationLogRepository
import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionType
import kr.passmate.support.FakeOpenAiClient
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * AI 문항 생성·재생성. OpenAI 는 Fake 로 갈아끼워 **실제 호출이 나가지 않는다**.
 */
@AutoConfigureMockMvc
@Transactional
class AiQuestionGenerationIntegrationTest : IntegrationTestSupport() {

    @TestConfiguration
    class FakeAiConfig {
        @Bean
        @Primary
        fun fakeOpenAiClient(): OpenAiClient = FakeOpenAiClient()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var openAiClient: OpenAiClient
    @Autowired private lateinit var logRepository: AiGenerationLogRepository
    @Autowired private lateinit var policy: PolicyProperties

    private val fake: FakeOpenAiClient get() = openAiClient as FakeOpenAiClient

    private lateinit var ownerToken: String
    private lateinit var otherToken: String
    private var ownerUserId: Long = 0

    @BeforeEach
    fun setUp() {
        fake.reset()
        val owner = register("ai-owner")
        ownerUserId = owner.first
        ownerToken = owner.second
        otherToken = register("ai-other").second
    }

    @Test
    fun `조건대로 문항을 만들어 세트 끝에 붙인다`() {
        val setId = createSet()
        // 직접 쓴 문항이 하나 있어도 AI 문항은 그 뒤에 붙는다(혼합 구성)
        addManualQuestion(setId, """{"type":"OX","content":"직접 쓴 문항","answer":"O"}""")

        generate(setId, """{"topic":"자료구조","counts":{"MCQ":2,"ESSAY":1},"difficulty":"HARD"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].orderNo").value(2))
            .andExpect(jsonPath("$[0].type").value("MCQ"))
            .andExpect(jsonPath("$[2].type").value("ESSAY"))

        mockMvc.perform(get("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(jsonPath("$.set.questionCount").value(4))
            .andExpect(jsonPath("$.set.source").value("MIXED"))
            .andExpect(jsonPath("$.questions[1].difficulty").value("HARD"))

        // 이미 있는 문항은 "피할 목록"으로 넘어간다 — 같은 문제를 또 만들지 않게 한다
        assertThat(fake.lastRequest!!.avoid).contains("직접 쓴 문항")
        assertThat(fake.lastRequest!!.topic).isEqualTo("자료구조")
    }

    @Test
    fun `형식 오류는 한 번 재시도하고 성공하면 정상 응답한다`() {
        val setId = createSet()
        fake.failTimes(1)

        generate(setId, """{"topic":"네트워크","counts":{"OX":2}}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.length()").value(2))

        assertThat(fake.callCount).isEqualTo(2)
        assertThat(successCount()).isEqualTo(1)
    }

    @Test
    fun `두 번 다 실패하면 502 이고 무료 횟수는 깎이지 않는다`() {
        val setId = createSet()
        fake.failTimes(2)

        generate(setId, """{"topic":"네트워크","counts":{"OX":2}}""")
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("AI_GENERATION_FAILED"))

        assertThat(fake.callCount).isEqualTo(2)
        assertThat(successCount()).isZero()
        assertThat(logRepository.findAll().single().status).isEqualTo(AiGenerationStatus.FAILED)
    }

    @Test
    fun `재시도해도 소용없는 실패는 한 번만 호출한다`() {
        val setId = createSet()
        fake.failTimes(2, retryable = false)

        generate(setId, """{"topic":"네트워크","counts":{"OX":1}}""")
            .andExpect(status().isBadGateway)

        // 인증 실패 같은 건 다시 걸어도 같은 결과다 — 호출을 한 번 더 낭비하지 않는다
        assertThat(fake.callCount).isEqualTo(1)
    }

    @Test
    fun `무료 횟수를 다 쓰면 429 로 막고 호출하지 않는다`() {
        val setId = createSet()
        repeat(policy.aiFreeLimit) {
            generate(setId, """{"topic":"주제 $it","counts":{"OX":1}}""").andExpect(status().isCreated)
        }
        val callsSoFar = fake.callCount

        generate(setId, """{"topic":"한도 초과","counts":{"OX":1}}""")
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("AI_FREE_LIMIT_EXCEEDED"))

        // 한도 검사는 호출 전에 한다 — 막힐 요청에 돈을 쓰지 않는다
        assertThat(fake.callCount).isEqualTo(callsSoFar)
    }

    @Test
    fun `확정된 세트와 남의 세트는 생성 전에 막는다`() {
        val confirmed = createSet().also {
            addManualQuestion(it, """{"type":"OX","content":"문항","answer":"O"}""")
            mockMvc.perform(post("/question-sets/{id}/confirm", it).header("Authorization", "Bearer $ownerToken"))
                .andExpect(status().isOk)
        }

        generate(confirmed, """{"topic":"주제","counts":{"OX":1}}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_ALREADY_CONFIRMED"))

        val mine = createSet()
        mockMvc.perform(
            post("/question-sets/{id}/questions/generate", mine)
                .header("Authorization", "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"topic":"주제","counts":{"OX":1}}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_QUESTION_SET_OWNER"))

        assertThat(fake.callCount).isZero()
    }

    @Test
    fun `한 번에 만들 수 있는 양을 넘기면 400 이다`() {
        val setId = createSet()

        generate(setId, """{"topic":"주제","counts":{"MCQ":30}}""")
            .andExpect(status().isBadRequest)
        generate(setId, """{"topic":"주제","counts":{"MCQ":0}}""")
            .andExpect(status().isBadRequest)

        assertThat(fake.callCount).isZero()
    }

    @Test
    fun `재생성은 같은 자리에 새 내용을 넣고 무료 횟수를 깎지 않는다`() {
        val setId = createSet()
        generate(setId, """{"topic":"운영체제","counts":{"MCQ":1},"difficulty":"EASY"}""")
            .andExpect(status().isCreated)

        val questionId = questionsOf(setId)[0].get("id").asLong()
        fake.respondWith(
            listOf(
                GeneratedQuestion(
                    type = QuestionType.MCQ,
                    content = "다시 만든 문항",
                    choices = listOf("가", "나", "다", "라"),
                    answer = "다",
                    explanation = "새 해설",
                    difficulty = Difficulty.EASY,
                ),
            ),
        )

        mockMvc.perform(
            post("/question-sets/{id}/questions/{qid}/regenerate", setId, questionId)
                .header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(questionId))
            .andExpect(jsonPath("$.content").value("다시 만든 문항"))
            .andExpect(jsonPath("$.orderNo").value(1))
            .andExpect(jsonPath("$.answer").value("다"))

        // 조건은 요청 본문이 아니라 기존 문항에서 읽는다
        assertThat(fake.lastRequest!!.counts).isEqualTo(mapOf(QuestionType.MCQ to 1))
        assertThat(fake.lastRequest!!.topic).isEqualTo("운영체제")

        // 생성 1회만 한도에 잡히고, 재생성은 REGENERATE 로 따로 남는다
        assertThat(successCount()).isEqualTo(1)
        assertThat(logRepository.findAll().map { it.kind })
            .containsExactly(AiGenerationKind.SET, AiGenerationKind.REGENERATE)
    }

    @Test
    fun `없는 문항은 재생성할 수 없다`() {
        val setId = createSet()

        mockMvc.perform(
            post("/question-sets/{id}/questions/{qid}/regenerate", setId, 999_999)
                .header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))

        assertThat(fake.callCount).isZero()
    }

    // ---------- helpers ----------

    private fun successCount(): Int =
        logRepository.countByUserIdAndKindAndStatus(
            ownerUserId,
            AiGenerationKind.SET,
            AiGenerationStatus.SUCCESS,
        ).toInt()

    private fun register(key: String): Pair<Long, String> {
        val outcome = userService.loginOrRegister(AuthProvider.GOOGLE, "ai-$key", "$key@example.com", key, null)
        return outcome.user.id to jwtTokenProvider.issue(outcome.user.id, outcome.user.isAdmin).accessToken
    }

    private fun createSet(title: String = "AI 세트"): Long =
        mockMvc.perform(
            post("/question-sets").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"title":"$title"}"""),
        ).andExpect(status().isCreated).andReturn().json().get("id").asLong()

    private fun addManualQuestion(setId: Long, body: String): ResultActions =
        mockMvc.perform(
            post("/question-sets/{id}/questions", setId).header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isCreated)

    private fun generate(setId: Long, body: String): ResultActions =
        mockMvc.perform(
            post("/question-sets/{id}/questions/generate", setId)
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        )

    private fun questionsOf(setId: Long): List<JsonNode> =
        mockMvc.perform(get("/question-sets/{id}", setId).header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk).andReturn().json().get("questions").toList()

    private fun org.springframework.test.web.servlet.MvcResult.json(): JsonNode =
        objectMapper.readTree(response.contentAsByteArray)
}
