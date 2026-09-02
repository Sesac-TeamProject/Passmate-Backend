package kr.passmate.feedback.service

import kr.passmate.common.event.AnswerScoreAdjustedEvent
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.feedback.domain.TeacherReview
import kr.passmate.feedback.dto.TeacherReviewRequest
import kr.passmate.feedback.dto.TeacherReviewResponse
import kr.passmate.feedback.repository.TeacherReviewRepository
import kr.passmate.question.domain.QuestionType
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.session.service.AnswerService
import kr.passmate.session.service.SessionQueryService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 선생님 첨삭 등록·수정 (FR-038, W-07 방 리포트 우측 패널).
 *
 * 답안당 한 장이라 upsert 다 — 두 번째 첨삭은 행을 늘리지 않고 덮어쓴다.
 */
@Service
class TeacherReviewService(
    private val roomQueryService: RoomQueryService,
    private val sessionQueryService: SessionQueryService,
    private val answerQueryService: AnswerQueryService,
    private val answerService: AnswerService,
    private val teacherReviewRepository: TeacherReviewRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun upsert(
        roomId: Long,
        answerId: Long,
        hostUserId: Long,
        request: TeacherReviewRequest,
    ): TeacherReviewResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)

        val answer = answerQueryService.getAnswer(answerId)
        // 남의 방 답안 id 를 이 방 경로에 끼워 넣어도 통과하지 않게 한다
        val sq = sessionQueryService.sessionQuestions(roomId)
            .firstOrNull { it.id == answer.sessionQuestionId }
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "이 방의 답안이 아닙니다.")
        val question = sessionQueryService.findQuestion(room, sq.questionId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND)

        val adjusted = request.adjustedScore
        if (adjusted != null) {
            // 객관식·OX 는 자동 채점 결과가 정답률·응답 분포와 묶여 있다.
            // 점수만 손대면 "정답률 50%인데 전원 만점" 같은 통계가 남는다
            if (question.type != QuestionType.ESSAY) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "점수 보정은 서술형 문항만 할 수 있습니다.")
            }
            if (adjusted > question.points) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "보정 점수는 배점(${question.points})을 넘을 수 없습니다.")
            }
        }

        val review = teacherReviewRepository.findByAnswerId(answerId)
            ?.apply { update(request.comment, adjusted, request.improvement) }
            ?: teacherReviewRepository.save(
                TeacherReview(
                    answerId = answerId,
                    reviewerUserId = hostUserId,
                    comment = request.comment,
                    adjustedScore = adjusted,
                    improvement = request.improvement,
                ),
            )

        // 보정을 지우면(null) 채점기가 낸 잠정 점수로 되돌린다
        val before = answer.finalScore
        val finalScore = adjusted ?: answer.score
        answerService.adjustFinalScore(answerId, finalScore)

        // 점수가 그대로면(코멘트만 단 경우) 등수·리포트를 다시 계산할 이유가 없다
        if (before != finalScore) {
            eventPublisher.publishEvent(AnswerScoreAdjustedEvent(roomId))
        }

        return TeacherReviewResponse.of(review, answer.participantId, finalScore)
    }
}
