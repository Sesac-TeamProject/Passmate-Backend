package kr.passmate.feedback.service

import kr.passmate.ai.client.EssayAnalysisResult
import kr.passmate.ai.service.AiAnalysisService
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransactionType
import kr.passmate.coin.service.CoinService
import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.feedback.domain.AiFeedback
import kr.passmate.feedback.domain.AiFeedbackStatus
import kr.passmate.feedback.repository.AiFeedbackRepository
import kr.passmate.question.domain.QuestionType
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.session.service.SessionQueryService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/** 분석 현황 한 벌. 요청 응답과 조회 응답이 같은 값을 쓴다. */
data class AnalysisState(
    val feedback: AiFeedback?,
    val remainingFreeCount: Int,
    val coinCost: Int,
)

/**
 * 서술형 답안 AI 분석 (FR-075).
 *
 * **학생이 요청할 때만 실행한다.** 세션이 끝날 때 전원 자동 분석을 돌리면
 * 분석 비용이 참가자 수 × 서술형 문항 수만큼 무제한으로 늘어난다.
 *
 * 회원 전용 · 월 5회 무료 · 초과분은 본인 코인 차감(부족하면 402) · 실패하면 환급.
 * 실제 호출은 커밋 뒤 비동기로 넘긴다 — 요청 응답은 곧바로 PENDING 으로 돌아간다.
 */
@Service
class EssayAnalysisService(
    private val roomQueryService: RoomQueryService,
    private val sessionQueryService: SessionQueryService,
    private val answerQueryService: AnswerQueryService,
    private val aiAnalysisService: AiAnalysisService,
    private val coinService: CoinService,
    private val aiFeedbackRepository: AiFeedbackRepository,
    private val policy: PolicyProperties,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 내 서술형 답안의 분석을 요청한다.
     *
     * 이미 진행 중이거나 끝난 건은 **그대로 돌려준다** — 버튼을 두 번 눌렀다고
     * 코인을 두 번 받지 않는다. 실패한 건만 같은 줄을 되써서 다시 건다.
     */
    @Transactional
    fun request(roomId: Long, questionId: Long, principal: AuthPrincipal): AnalysisState {
        val userId = (principal as? UserPrincipal)?.userId
            ?: throw BusinessException(ErrorCode.GUEST_NOT_ALLOWED, "AI 분석은 회원만 이용할 수 있습니다. 로그인해 주세요.")

        // 키가 없으면 조용히 실패하지 않는다. 코인을 받기 **전에** 502 로 막는다
        if (!aiAnalysisService.isConfigured) {
            throw BusinessException(ErrorCode.AI_ANALYSIS_FAILED, "AI 분석이 아직 설정되지 않았습니다.")
        }

        val room = roomQueryService.getRoom(roomId)
        val question = sessionQueryService.findQuestion(room, questionId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND, "이 방에서 출제된 문항이 아닙니다.")
        if (question.type != QuestionType.ESSAY) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "서술형 답안만 AI 분석을 받을 수 있습니다.")
        }
        val modelAnswer = question.answer
            ?: throw BusinessException(ErrorCode.INVALID_QUESTION, "모범답안이 없어 분석 기준을 세울 수 없습니다.")

        val answer = answerQueryService.getMyAnswer(roomId, questionId, principal)
        val existing = aiFeedbackRepository.findByAnswerId(answer.id)
        if (existing != null && !existing.isFailed) {
            return state(existing, userId)
        }

        // 무료 한도가 남아 있으면 0, 아니면 정책 단가. 잔액이 모자라면 여기서 402 가 난다
        val charge = if (freeUsedThisMonth(userId) < policy.essayAnalysisFreeLimit) 0 else policy.essayAnalysisCoinCost

        val feedback = existing?.apply { retry(charge) }
            ?: aiFeedbackRepository.save(AiFeedback(answerId = answer.id, userId = userId, chargedCoins = charge))
        // 원장이 가리킬 ref_id 가 필요하다
        aiFeedbackRepository.flush()

        if (charge > 0) {
            coinService.deduct(
                userId = userId,
                amount = charge,
                type = CoinTransactionType.AI_ANALYSIS,
                refType = CoinRefType.AI_FEEDBACK,
                refId = feedback.id,
                memo = "서술형 AI 분석",
            )
        }

        eventPublisher.publishEvent(
            EssayAnalysisRequestedEvent(
                feedbackId = feedback.id,
                questionContent = question.content,
                modelAnswer = modelAnswer,
                submitted = answer.submitted,
            ),
        )
        return state(feedback, userId)
    }

    /** 분석 성공. 비동기 스레드가 부른다. */
    @Transactional
    fun complete(feedbackId: Long, result: EssayAnalysisResult) {
        val feedback = aiFeedbackRepository.findById(feedbackId).orElse(null) ?: return
        feedback.complete(
            keyPoints = result.keyPoints,
            missingPoints = result.missingPoints,
            suggestions = result.suggestions,
            summary = result.summary,
            model = result.model,
            latencyMs = result.durationMs,
        )
    }

    /**
     * 분석 실패. 차감분이 있으면 **환급까지 같은 트랜잭션에서** 끝낸다.
     * 환급은 원장 기준 멱등이라 콜백이 겹쳐도 두 번 돌려주지 않는다.
     */
    @Transactional
    fun fail(feedbackId: Long, message: String?) {
        val feedback = aiFeedbackRepository.findById(feedbackId).orElse(null) ?: return
        val charged = feedback.chargedCoins
        feedback.fail(message)

        if (charged > 0) {
            coinService.refund(
                userId = feedback.userId,
                amount = charged,
                refType = CoinRefType.AI_FEEDBACK,
                refId = feedbackId,
                memo = "서술형 AI 분석 실패 환급",
            )
            feedback.clearCharge()
            log.info("분석 실패로 코인을 환급했다 feedbackId={} amount={}", feedbackId, charged)
        }
    }

    /** 이번 달 남은 무료 분석 횟수. 화면에 "무료 n회 남음"으로 띄운다. */
    @Transactional(readOnly = true)
    fun remainingFreeCount(userId: Long): Int =
        (policy.essayAnalysisFreeLimit - freeUsedThisMonth(userId)).coerceAtLeast(0)

    fun state(feedback: AiFeedback?, userId: Long): AnalysisState = AnalysisState(
        feedback = feedback,
        remainingFreeCount = remainingFreeCount(userId),
        coinCost = policy.essayAnalysisCoinCost,
    )

    /**
     * 이번 달 무료로 쓴 건수. 달 경계는 저장 시각(createdAt)과 같은 시계로 잡는다 —
     * 엔티티가 LocalDateTime.now() 로 찍히므로 여기도 LocalDate.now() 를 쓴다.
     */
    private fun freeUsedThisMonth(userId: Long): Int {
        val from: LocalDateTime = LocalDate.now().withDayOfMonth(1).atStartOfDay()
        return aiFeedbackRepository
            .countFreeUsage(userId, AiFeedbackStatus.FAILED, from, from.plusMonths(1))
            .toInt()
    }
}
