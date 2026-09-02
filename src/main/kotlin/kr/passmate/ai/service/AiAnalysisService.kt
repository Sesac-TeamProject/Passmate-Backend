package kr.passmate.ai.service

import kr.passmate.ai.client.AiCallException
import kr.passmate.ai.client.AiProperties
import kr.passmate.ai.client.EssayAnalysisRequest
import kr.passmate.ai.client.EssayAnalysisResult
import kr.passmate.ai.client.OpenAiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Semaphore

/**
 * 서술형 답안 분석. AI 기능을 쓰는 다른 기능 패키지는 Client 가 아니라 이 Service 를 부른다.
 *
 * **동시 실행을 세마포어로 묶는다.** 한 세션이 끝나면 학생들이 한꺼번에 "AI 분석 보기"를 누르는데,
 * 그때마다 스레드가 30초씩 OpenAI 를 기다리면 실시간 세션 경로가 굶는다.
 * 자리가 없으면 기다렸다 들어간다 — 요청을 버리지는 않는다(호출자가 이미 코인을 냈다).
 *
 * 트랜잭션을 열지 않는다. 외부 호출은 트랜잭션 밖에서 하고 결과만 호출자가 반영한다.
 */
@Service
class AiAnalysisService(
    private val openAiClient: OpenAiClient,
    private val properties: AiProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val semaphore = Semaphore(properties.maxConcurrentAnalysis)

    /** 호출할 준비가 됐는지. 코인을 차감하기 **전에** 물어보는 자리다. */
    val isConfigured: Boolean get() = openAiClient.isConfigured

    /**
     * 분석 1건. 형식 오류처럼 다시 걸어볼 만한 실패는 **한 번만** 재시도한다.
     * 재시도까지 실패하면 [AiCallException] 이 그대로 올라가고, 호출자가 FAILED 로 마감·환급한다.
     */
    fun analyze(request: EssayAnalysisRequest): EssayAnalysisResult {
        semaphore.acquire()
        try {
            var lastError: AiCallException? = null
            for (attempt in 0..MAX_RETRY) {
                try {
                    return openAiClient.analyzeEssay(request)
                } catch (e: AiCallException) {
                    lastError = e
                    log.warn("서술형 분석 실패 attempt={} retryable={}", attempt, e.retryable)
                    if (!e.retryable) break
                }
            }
            throw checkNotNull(lastError)
        } finally {
            semaphore.release()
        }
    }

    private companion object {
        /** 문제 생성과 같은 원칙 — 형식 오류는 1회만 다시 걸어본다 */
        const val MAX_RETRY = 1
    }
}
