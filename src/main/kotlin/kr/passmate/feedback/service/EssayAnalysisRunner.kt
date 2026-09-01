package kr.passmate.feedback.service

import kr.passmate.ai.client.EssayAnalysisRequest
import kr.passmate.ai.service.AiAnalysisService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 실제 OpenAI 호출을 도는 자리. **요청 트랜잭션이 커밋된 뒤에, 다른 스레드에서** 돈다.
 *
 * - AFTER_COMMIT: PENDING 행이 커밋되기 전에 결과를 쓰면 갱신 대상이 없다
 * - @Async: 요청 스레드는 곧바로 PENDING 을 돌려주고 빠진다. 세션 실시간 경로를 막지 않는다
 *
 * 어떤 실패든 삼키지 않고 FAILED 로 마감한다 — 그래야 차감분이 환급되고
 * 학생 화면이 PENDING 에서 영원히 멈추지 않는다.
 */
@Component
class EssayAnalysisRunner(
    private val aiAnalysisService: AiAnalysisService,
    private val essayAnalysisService: EssayAnalysisService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: EssayAnalysisRequestedEvent) {
        try {
            val result = aiAnalysisService.analyze(
                EssayAnalysisRequest(
                    questionContent = event.questionContent,
                    modelAnswer = event.modelAnswer,
                    submitted = event.submitted,
                ),
            )
            essayAnalysisService.complete(event.feedbackId, result)
        } catch (e: Exception) {
            log.warn("서술형 분석 실패 feedbackId={}", event.feedbackId, e)
            essayAnalysisService.fail(event.feedbackId, e.message)
        }
    }
}
