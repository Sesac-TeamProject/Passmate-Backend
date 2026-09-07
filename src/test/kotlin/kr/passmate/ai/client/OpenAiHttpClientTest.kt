package kr.passmate.ai.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionType
import org.hamcrest.Matchers.contains
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

/**
 * OpenAI HTTP 층.
 *
 * ⚠️ **base URL 이 `http://localhost:1` 이다.** 목 서버가 어떤 이유로 무력화돼도
 * 요청이 실제 OpenAI 로 나가지 않게 하려는 안전장치다 — 유료 API 는 테스트에서
 * 어떤 형태로도 부르지 않는다(.claude/CLAUDE.md ⛔ 규칙).
 */
class OpenAiHttpClientTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var client: OpenAiHttpClient

    private val properties = AiProperties(
        provider = "openai",
        baseUrl = LOCAL_DEAD_END,
        apiKey = "test-key",
        generationModel = "test-gen",
        analysisModel = "test-analysis",
        reasoningEffort = "",
        timeoutSeconds = 5,
        maxConcurrentAnalysis = 1,
    )

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        server = MockRestServiceServer.bindTo(builder).build()
        client = OpenAiHttpClient(properties, jacksonObjectMapper(), builder.build())
    }

    @Test
    fun `요청이 주입받은 RestClient 로 나간다`() {
        // 이 테스트가 실패하면 클라이언트가 요청 팩토리를 스스로 갈아끼워
        // 목 서버를 밀어냈다는 뜻이다 — 즉 실제 OpenAI 로 나갈 수 있는 상태다
        expectChat(analysisBody())

        client.analyzeEssay(request())

        server.verify()
    }

    @Test
    fun `API 키를 Bearer 로 보낸다`() {
        server.expect(requestTo("$LOCAL_DEAD_END/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andRespond(json(analysisBody()))

        client.analyzeEssay(request())

        server.verify()
    }

    @Test
    fun `분석 결과를 스키마대로 읽는다`() {
        expectChat(analysisBody())

        val result = client.analyzeEssay(request())

        assertThat(result.keyPoints).containsExactly("핵심 하나")
        assertThat(result.missingPoints).containsExactly("빠진 것")
        assertThat(result.suggestions).containsExactly("이렇게 써보자")
        assertThat(result.summary).isEqualTo("전반적으로 좋다")
    }

    @Test
    fun `총평이 비어 있으면 재시도 가능한 실패로 본다`() {
        expectChat(analysisBody(summary = ""))

        assertThatThrownBy { client.analyzeEssay(request()) }
            .isInstanceOf(AiCallException::class.java)
            .extracting { (it as AiCallException).retryable }
            .isEqualTo(true)
    }

    @Test
    fun `키가 없으면 호출조차 하지 않는다`() {
        val unconfigured = OpenAiHttpClient(
            properties.copy(apiKey = ""), jacksonObjectMapper(), RestClient.builder().build(),
        )

        assertThatThrownBy { unconfigured.analyzeEssay(request()) }
            .isInstanceOf(Exception::class.java)
        // 목에 아무 요청도 오지 않았다
        server.verify()
    }

    @Test
    fun `문항 생성 스키마의 type enum 은 요청한 유형만 담는다`() {
        // 세 유형을 다 열어 두면 "객관식 8개" 요청에 OX 가 섞여 나와 검증에서 떨어진다(웹 버그 리포트 B-23).
        // 스키마가 유형을 하나로 못 박으면 모델이 다른 유형을 낼 방법 자체가 없다
        server.expect(requestTo("$LOCAL_DEAD_END/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath(TYPE_ENUM_PATH, contains("MCQ")))
            .andRespond(json(generationBody(mcq("첫째"), mcq("둘째"))))

        val result = client.generateQuestions(generationRequest(mapOf(QuestionType.MCQ to 2)))

        assertThat(result.questions).hasSize(2)
        assertThat(result.questions.map { it.type }).containsOnly(QuestionType.MCQ)
        server.verify()
    }

    @Test
    fun `요청과 다른 유형이 섞여 오면 재시도 가능한 실패로 본다`() {
        expectChat(generationBody(mcq("첫째"), ox("둘째")))

        assertThatThrownBy { client.generateQuestions(generationRequest(mapOf(QuestionType.MCQ to 2))) }
            .isInstanceOf(AiCallException::class.java)
            .hasMessageContaining("유형별 개수")
            .extracting { (it as AiCallException).retryable }
            .isEqualTo(true)
    }

    private fun generationRequest(counts: Map<QuestionType, Int>) = AiGenerationRequest(
        topic = "야구",
        counts = counts,
        difficulty = Difficulty.NORMAL,
    )

    private fun request() = EssayAnalysisRequest(
        questionContent = "질문",
        modelAnswer = "모범답안",
        submitted = "학생 답안",
    )

    private fun expectChat(body: String) {
        server.expect(requestTo("$LOCAL_DEAD_END/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(json(body))
    }

    private fun json(body: String) =
        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body)

    /** OpenAI 가 Structured Outputs 로 돌려주는 모양. content 안이 다시 JSON 문자열이다 */
    private fun analysisBody(summary: String = "전반적으로 좋다"): String {
        val payload = """{"keyPoints":["핵심 하나"],"missingPoints":["빠진 것"],""" +
            """"suggestions":["이렇게 써보자"],"summary":"$summary"}"""
        val escaped = payload.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"model":"test-analysis","choices":[{"message":{"content":"$escaped"}}]}"""
    }

    private fun mcq(content: String) =
        """{"type":"MCQ","content":"$content","choices":["가","나","다","라"],"answer":"가","explanation":null,"difficulty":"NORMAL"}"""

    private fun ox(content: String) =
        """{"type":"OX","content":"$content","choices":null,"answer":"O","explanation":null,"difficulty":"NORMAL"}"""

    /** 문항 생성의 Structured Outputs 응답. questions 배열이 content 안에 JSON 문자열로 들어 있다 */
    private fun generationBody(vararg questions: String): String {
        val payload = """{"questions":[${questions.joinToString(",")}]}"""
        val escaped = payload.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"model":"test-gen","choices":[{"message":{"content":"$escaped"}}]}"""
    }

    private companion object {
        /** 아무도 듣지 않는 포트. 목이 무력화되면 연결 거부로 시끄럽게 실패한다 */
        const val LOCAL_DEAD_END = "http://localhost:1"

        /** 요청 본문에서 문항 type 의 enum 이 놓이는 자리 */
        const val TYPE_ENUM_PATH = "$.response_format.json_schema.schema.properties.questions.items.properties.type.enum"
    }
}
