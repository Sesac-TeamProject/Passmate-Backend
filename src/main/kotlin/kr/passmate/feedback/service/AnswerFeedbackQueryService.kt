package kr.passmate.feedback.service

import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.feedback.dto.AnalysisStatus
import kr.passmate.feedback.dto.EssayAnalysisView
import kr.passmate.feedback.dto.MyAnswerResponse
import kr.passmate.feedback.repository.AiFeedbackRepository
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.session.service.SessionQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 내 답안·AI 피드백 조회.
 *
 * 게스트도 자기 답안과 점수는 볼 수 있다. 분석만 회원 전용이라,
 * 게스트에게는 상태를 NOT_REQUESTED 로, 남은 무료 횟수를 null 로 준다.
 */
@Service
@Transactional(readOnly = true)
class AnswerFeedbackQueryService(
    private val roomQueryService: RoomQueryService,
    private val sessionQueryService: SessionQueryService,
    private val answerQueryService: AnswerQueryService,
    private val essayAnalysisService: EssayAnalysisService,
    private val aiFeedbackRepository: AiFeedbackRepository,
    private val policy: PolicyProperties,
) {

    fun myAnswer(roomId: Long, questionId: Long, principal: AuthPrincipal): MyAnswerResponse {
        val room = roomQueryService.getRoom(roomId)
        val sq = sessionQueryService.findSessionQuestion(roomId, questionId)
        val question = sessionQueryService.findQuestion(room, questionId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND, "이 방에서 출제된 문항이 아닙니다.")

        val answer = answerQueryService.getMyAnswer(roomId, questionId, principal)
        val feedback = aiFeedbackRepository.findByAnswerId(answer.id)
        val userId = (principal as? UserPrincipal)?.userId

        return MyAnswerResponse(
            roomId = roomId,
            sessionQuestionId = sq.id,
            questionId = questionId,
            orderNo = sq.orderNo,
            type = question.type,
            content = question.content,
            points = question.points,
            submitted = answer.submitted,
            isCorrect = answer.isCorrect,
            score = answer.score,
            finalScore = answer.finalScore,
            submittedAt = answer.submittedAt,
            // 마감 전에는 정답·해설을 내보내지 않는다 (QUESTION_STARTED 와 같은 원칙)
            answer = question.answer.takeIf { sq.isEnded },
            explanation = question.explanation?.takeIf { sq.isEnded },
            analysisStatus = AnalysisStatus.of(feedback),
            analysis = feedback?.let { EssayAnalysisView.from(it) },
            remainingFreeAnalysis = userId?.let { essayAnalysisService.remainingFreeCount(it) },
            analysisCoinCost = policy.essayAnalysisCoinCost,
        )
    }
}
