package kr.passmate.feedback.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.passmate.ai.client.AiCallException
import kr.passmate.ai.client.EssayAnalysisRequest
import kr.passmate.ai.client.EssayAnalysisResult
import kr.passmate.ai.service.AiAnalysisService
import org.junit.jupiter.api.Test

/**
 * 비동기로 도는 자리라 실패를 삼키면 학생 화면이 PENDING 에서 멈추고 코인도 안 돌아온다.
 * 어떤 실패든 FAILED 로 마감하는지가 핵심이다.
 */
class EssayAnalysisRunnerTest {

    private val aiAnalysisService = mockk<AiAnalysisService>()
    private val essayAnalysisService = mockk<EssayAnalysisService>(relaxed = true)
    private val runner = EssayAnalysisRunner(aiAnalysisService, essayAnalysisService)

    private val event = EssayAnalysisRequestedEvent(
        feedbackId = 42,
        questionContent = "TCP 를 설명하시오",
        modelAnswer = "연결지향 프로토콜",
        submitted = "연결을 맺고 주고받습니다",
    )

    @Test
    fun `분석에 성공하면 결과를 반영한다`() {
        val result = EssayAnalysisResult(
            keyPoints = listOf("연결지향"),
            missingPoints = emptyList(),
            suggestions = listOf("예시를 덧붙여 보세요"),
            summary = "좋습니다.",
            model = "fake-analysis",
            durationMs = 10,
        )
        every { aiAnalysisService.analyze(any<EssayAnalysisRequest>()) } returns result

        runner.on(event)

        verify(exactly = 1) { essayAnalysisService.complete(42, result) }
        verify(exactly = 0) { essayAnalysisService.fail(any(), any()) }
    }

    @Test
    fun `AI 호출이 실패하면 FAILED 로 마감한다`() {
        every { aiAnalysisService.analyze(any<EssayAnalysisRequest>()) } throws
            AiCallException("형식 오류", retryable = true)

        runner.on(event)

        verify(exactly = 1) { essayAnalysisService.fail(42, "형식 오류") }
    }

    @Test
    fun `예상 못 한 오류도 삼키지 않고 FAILED 로 마감한다`() {
        every { aiAnalysisService.analyze(any<EssayAnalysisRequest>()) } throws IllegalStateException("어딘가 터짐")

        runner.on(event)

        verify(exactly = 1) { essayAnalysisService.fail(42, "어딘가 터짐") }
    }
}
