package kr.passmate.ai.service

import kr.passmate.ai.client.AiCallException
import kr.passmate.ai.client.AiGenerationRequest
import kr.passmate.ai.client.AiGenerationResult
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

    /** 세트에 붙일 문항 여러 개. */
    fun generateForSet(userId: Long, setId: Long, request: AiGenerationRequest): List<GeneratedQuestion> {
        verifyFreeLimit(userId)
        return call(userId, setId, AiGenerationKind.SET, request)
    }

    /**
     * 문항 하나를 같은 조건으로 다시 만든다.
     * **재생성도 무료 한도를 깎는다** — 문항 수가 적을 뿐 AI 를 한 번 더 부르는 것은 같다.
     * 한도를 두지 않으면 재생성 버튼이 곧 무제한 유료 호출이 된다.
     */
    fun regenerate(userId: Long, setId: Long, request: AiGenerationRequest): GeneratedQuestion {
        verifyFreeLimit(userId)
        return call(userId, setId, AiGenerationKind.REGENERATE, request).first()
    }

    /** 남은 무료 횟수. 화면에 "AI 생성 n회 남음"을 띄우는 데 쓴다. 생성·재생성을 합친 값이다. */
    fun remainingFreeCount(userId: Long): Int =
        (policy.aiFreeLimit - successCount(userId)).coerceAtLeast(0)

    private fun verifyFreeLimit(userId: Long) {
        if (successCount(userId) >= policy.aiFreeLimit) {
            throw BusinessException(
                ErrorCode.AI_FREE_LIMIT_EXCEEDED,
                "AI 문항 생성·재생성은 합쳐서 ${policy.aiFreeLimit}회까지 무료입니다. 직접 문항을 추가해 주세요.",
            )
        }
    }

    /** 생성·재생성을 가리지 않고 성공한 호출을 센다. */
    private fun successCount(userId: Long): Int =
        logRepository.countByUserIdAndStatus(userId, AiGenerationStatus.SUCCESS).toInt()

    /**
     * 유형별로 나눠 호출한다 — 한 호출에 유형을 섞으면 모델이 분포를 지키지 않아
     * 재시도까지 같은 이유로 떨어졌다(웹 버그 리포트 B-23). 각 호출은 실패하면
     * 재시도 가능한 것만 **한 번 더** 걸고, 그래도 안 되면 전체를 502 로 끝낸다.
     *
     * 사용자 액션은 하나라 로그도 한 줄, 무료 횟수도 한 번이다.
     * 앞 유형이 성공하고 뒤 유형이 실패하면 **앞 결과도 버린다** —
     * 절반만 붙이면 "객관식 5·서술형 3"을 약속한 화면과 어긋난다.
     * 실패는 SUCCESS 로 기록되지 않으므로 무료 횟수가 깎이지 않는다.
     */
    private fun call(
        userId: Long,
        setId: Long,
        kind: AiGenerationKind,
        request: AiGenerationRequest,
    ): List<GeneratedQuestion> {
        val parts = request.splitByType()
        if (parts.isEmpty()) throw BusinessException(ErrorCode.INVALID_INPUT, "생성할 문항 수가 없습니다.")

        val questions = mutableListOf<GeneratedQuestion>()
        var model: String? = null
        var durationMs = 0
        var retryCount = 0

        try {
            for (part in parts) {
                // 앞 유형에서 만든 문항도 피할 목록에 넣는다 — 유형만 다른 같은 문제가 나오지 않게
                val (result, retries) = callWithRetry(setId, kind, part.copy(avoid = request.avoid + questions.map { it.content }))
                questions += result.questions
                model = model ?: result.model
                durationMs += result.durationMs
                retryCount += retries
            }
        } catch (e: AiCallException) {
            val retriesOfFailedPart = if (e.retryable) MAX_RETRY else 0
            save(userId, setId, kind, request, AiGenerationStatus.FAILED, retryCount + retriesOfFailedPart, e.message, null, null)
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, cause = e)
        }

        save(userId, setId, kind, request, AiGenerationStatus.SUCCESS, retryCount, null, model, durationMs)
        return questions
    }

    /**
     * 유형 하나짜리 호출. 재시도 1회는 같은 요청의 연장이라 한도를 두 번 깎지 않는다.
     * 끝내 실패하면 마지막 예외를 그대로 던진다 — 재시도 여부는 호출자가 로그에 남긴다.
     * @return 결과와 그때까지 쓴 재시도 횟수
     */
    private fun callWithRetry(
        setId: Long,
        kind: AiGenerationKind,
        request: AiGenerationRequest,
    ): Pair<AiGenerationResult, Int> {
        var lastError: AiCallException? = null

        for (attempt in 0..MAX_RETRY) {
            try {
                return openAiClient.generateQuestions(request) to attempt
            } catch (e: AiCallException) {
                lastError = e
                log.warn(
                    "AI 생성 실패 setId={} kind={} types={} attempt={} retryable={}",
                    setId, kind, request.types, attempt, e.retryable,
                )
                if (!e.retryable) break
            }
        }
        throw checkNotNull(lastError)
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
