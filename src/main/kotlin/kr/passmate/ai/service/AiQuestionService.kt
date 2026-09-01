package kr.passmate.ai.service

import kr.passmate.ai.client.AiGenerationException
import kr.passmate.ai.client.AiGenerationRequest
import kr.passmate.ai.client.GeneratedQuestion
import kr.passmate.ai.client.OpenAiClient
import kr.passmate.ai.domain.AiGenerationKind
import kr.passmate.ai.domain.AiGenerationLog
import kr.passmate.ai.domain.AiGenerationStatus
import kr.passmate.ai.repository.AiGenerationLogRepository
import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * AI 문항 생성. 무료 한도를 지키고, 형식 오류는 1회 재시도하고, 결과를 로그로 남긴다.
 *
 * **트랜잭션을 열지 않는다.** OpenAI 호출은 수십 초가 걸릴 수 있어 그 시간만큼 커넥션을 쥐고 있으면
 * 세션 실시간 경로가 굶는다. 문항 저장은 호출이 끝난 뒤 question 기능이 자기 트랜잭션에서 한다.
 */
@Service
class AiQuestionService(
    private val openAiClient: OpenAiClient,
    private val logRepository: AiGenerationLogRepository,
    private val policy: PolicyProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 세트에 붙일 문항 여러 개. 무료 한도를 세는 쪽은 이 경로뿐이다. */
    fun generateForSet(userId: Long, setId: Long, request: AiGenerationRequest): List<GeneratedQuestion> {
        verifyFreeLimit(userId)
        return call(userId, setId, AiGenerationKind.SET, request)
    }

    /**
     * 문항 하나를 같은 조건으로 다시 만든다.
     * 재생성은 무료 한도를 깎지 않는다(kind=REGENERATE) — 마음에 들 때까지 고쳐 쓰는 동작이라서다.
     */
    fun regenerate(userId: Long, setId: Long, request: AiGenerationRequest): GeneratedQuestion =
        call(userId, setId, AiGenerationKind.REGENERATE, request).first()

    /** 남은 무료 횟수. 화면에 "AI 생성 n회 남음"을 띄우는 데 쓴다. */
    fun remainingFreeCount(userId: Long): Int =
        (policy.aiFreeLimit - successCount(userId)).coerceAtLeast(0)

    private fun verifyFreeLimit(userId: Long) {
        if (successCount(userId) >= policy.aiFreeLimit) {
            throw BusinessException(
                ErrorCode.AI_FREE_LIMIT_EXCEEDED,
                "AI 문항 생성은 ${policy.aiFreeLimit}회까지 무료입니다. 직접 문항을 추가해 주세요.",
            )
        }
    }

    private fun successCount(userId: Long): Int =
        logRepository.countByUserIdAndKindAndStatus(
            userId,
            AiGenerationKind.SET,
            AiGenerationStatus.SUCCESS,
        ).toInt()

    /**
     * 호출 → 실패하면 재시도 가능한 것만 **한 번 더** → 그래도 안 되면 502.
     * 실패는 SUCCESS 로 기록되지 않으므로 무료 횟수가 깎이지 않는다.
     */
    private fun call(
        userId: Long,
        setId: Long,
        kind: AiGenerationKind,
        request: AiGenerationRequest,
    ): List<GeneratedQuestion> {
        var lastError: AiGenerationException? = null

        for (attempt in 0..MAX_RETRY) {
            try {
                val result = openAiClient.generateQuestions(request)
                save(userId, setId, kind, request, AiGenerationStatus.SUCCESS, attempt, null, result.model, result.durationMs)
                return result.questions
            } catch (e: AiGenerationException) {
                lastError = e
                log.warn("AI 생성 실패 setId={} kind={} attempt={} retryable={}", setId, kind, attempt, e.retryable)
                if (!e.retryable) break
            }
        }

        val retryCount = if (lastError?.retryable == true) MAX_RETRY else 0
        save(userId, setId, kind, request, AiGenerationStatus.FAILED, retryCount, lastError?.message, null, null)
        throw BusinessException(ErrorCode.AI_GENERATION_FAILED, cause = lastError)
    }

    private fun save(
        userId: Long,
        setId: Long,
        kind: AiGenerationKind,
        request: AiGenerationRequest,
        status: AiGenerationStatus,
        retryCount: Int,
        errorMessage: String?,
        model: String?,
        durationMs: Int?,
    ) {
        logRepository.save(
            AiGenerationLog(
                setId = setId,
                userId = userId,
                kind = kind,
                params = mapOf(
                    "topic" to request.topic,
                    "counts" to request.counts.mapKeys { it.key.name },
                    "difficulty" to request.difficulty.name,
                    "hasMaterial" to (request.material != null),
                ),
                status = status,
                retryCount = retryCount,
                errorMessage = AiGenerationLog.truncate(errorMessage),
                model = model,
                durationMs = durationMs,
            ),
        )
    }

    private companion object {
        /** 명세: 형식 오류·생성 실패 시 자동 재시도 1회 (FR-015) */
        const val MAX_RETRY = 1
    }
}
