package kr.passmate.ai.service

import kr.passmate.ai.client.AiCallException
import kr.passmate.ai.client.AiProperties
import kr.passmate.ai.client.EssayAnalysisRequest
import kr.passmate.support.FakeOpenAiClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 유료 API 를 부르지 않는다 — Fake 로만 돈다.
 * 재시도가 "한 번만" 도는지가 핵심이다. 두 번 돌면 요금이 두 배가 된다.
 */
class AiAnalysisServiceTest {

    private val client = FakeOpenAiClient()
    private val service = AiAnalysisService(client, properties())

    private val request = EssayAnalysisRequest(
        questionContent = "삼권분립을 설명하시오.",
        modelAnswer = "입법·행정·사법이 서로를 견제한다.",
        submitted = "국가 권력을 셋으로 나눈 것이다.",
    )

    @Test
    fun `형식 오류는 한 번만 다시 걸어본다`() {
        client.failAnalysisTimes(1)

        val result = service.analyze(request)

        assertThat(result.summary).isNotBlank()
        assertThat(client.analysisCallCount).isEqualTo(2)
    }

    @Test
    fun `재시도해도 실패하면 예외를 그대로 올린다`() {
        client.failAnalysisTimes(2)

        assertThatThrownBy { service.analyze(request) }
            .isInstanceOf(AiCallException::class.java)

        // 최초 1회 + 재시도 1회. 그 이상 부르면 요금만 늘어난다
        assertThat(client.analysisCallCount).isEqualTo(2)
    }

    @Test
    fun `다시 걸어도 결과가 같은 실패는 재시도하지 않는다`() {
        client.failAnalysisTimes(1, retryable = false)

        assertThatThrownBy { service.analyze(request) }
            .isInstanceOf(AiCallException::class.java)

        assertThat(client.analysisCallCount).isEqualTo(1)
    }

    @Test
    fun `키가 비어 있으면 설정되지 않았다고 알린다`() {
        assertThat(AiAnalysisService(client, properties(apiKey = "")).isConfigured).isFalse()
        assertThat(service.isConfigured).isTrue()
    }

    private fun properties(apiKey: String = "test-key") = AiProperties(
        provider = "openai",
        baseUrl = "https://example.invalid/v1",
        apiKey = apiKey,
        generationModel = "fake-generation",
        analysisModel = "fake-analysis",
        reasoningEffort = "",
        timeoutSeconds = 30,
        maxConcurrentAnalysis = 2,
    )
}
