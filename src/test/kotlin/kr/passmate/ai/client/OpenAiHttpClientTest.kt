package kr.passmate.ai.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
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
        client = OpenAiHttpClient(properties, ObjectMapper(), builder.build())
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
            properties.copy(apiKey = ""), ObjectMapper(), RestClient.builder().build(),
        )

        assertThatThrownBy { unconfigured.analyzeEssay(request()) }
            .isInstanceOf(Exception::class.java)
        // 목에 아무 요청도 오지 않았다
        server.verify()
    }

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

    private companion object {
        /** 아무도 듣지 않는 포트. 목이 무력화되면 연결 거부로 시끄럽게 실패한다 */
        const val LOCAL_DEAD_END = "http://localhost:1"
    }
}
