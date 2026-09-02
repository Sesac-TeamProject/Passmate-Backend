package kr.passmate.feedback.service

import kr.passmate.feedback.dto.AnswerFeedbackView
import kr.passmate.feedback.dto.ReviewTargetAnswer
import kr.passmate.feedback.dto.ReviewTargetListResponse
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.session.service.SessionQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 첨삭 대상 답안 목록 (FR-038).
 *
 * 남의 답안이 통째로 나가므로 **호스트만** 본다.
 * 필터로 넘어온 문항·참가자는 그 방에 있는 것인지 먼저 확인한다 — 오타로 빈 목록이
 * 돌아오면 "첨삭할 답안이 없다"로 읽혀 잘못된 안심을 준다.
 */
@Service
@Transactional(readOnly = true)
class TeacherReviewQueryService(
    private val roomQueryService: RoomQueryService,
    private val sessionQueryService: SessionQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val answerQueryService: AnswerQueryService,
    private val answerFeedbackQueryService: AnswerFeedbackQueryService,
) {

    fun listReviewTargets(
        roomId: Long,
        hostUserId: Long,
        questionId: Long?,
        participantId: Long?,
    ): ReviewTargetListResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)

        // 없는 문항·참가자로 거르면 404 로 알린다
        val targetSessionQuestionId = questionId?.let { sessionQueryService.findSessionQuestion(roomId, it).id }
        participantId?.let { participantQueryService.getOfRoom(roomId, it) }

        val sessionQuestions = sessionQueryService.sessionQuestions(roomId).associateBy { it.id }
        val questions = sessionQueryService.questionsOf(room).associateBy { it.id }
        val participants = participantQueryService.listAll(roomId).associateBy { it.id }

        val answers = answerQueryService.listByRoom(roomId)
            .filter { targetSessionQuestionId == null || it.sessionQuestionId == targetSessionQuestionId }
            .filter { participantId == null || it.participantId == participantId }

        val feedbacks = answerFeedbackQueryService.viewsOf(answers.map { it.id })

        val rows = answers
            // 문항 순서대로, 같은 문항 안에서는 닉네임 순 — 패널에서 넘길 때 순서가 흔들리지 않게
            .sortedWith(
                compareBy(
                    { sessionQuestions[it.sessionQuestionId]?.orderNo ?: 0 },
                    { participants[it.participantId]?.nickname.orEmpty() },
                ),
            )
            .mapNotNull { answer ->
                val sq = sessionQuestions[answer.sessionQuestionId] ?: return@mapNotNull null
                val question = questions[sq.questionId] ?: return@mapNotNull null
                val participant = participants[answer.participantId] ?: return@mapNotNull null
                val feedback = feedbacks[answer.id] ?: AnswerFeedbackView.NONE

                ReviewTargetAnswer(
                    answerId = answer.id,
                    sessionQuestionId = sq.id,
                    questionId = question.id,
                    orderNo = sq.orderNo,
                    type = question.type,
                    questionContent = question.content,
                    points = question.points,
                    modelAnswer = question.answer,
                    participantId = participant.id,
                    nickname = participant.nickname,
                    avatarId = participant.avatarId,
                    submitted = answer.submitted,
                    isCorrect = answer.isCorrect,
                    score = answer.score,
                    finalScore = answer.finalScore,
                    submittedAt = answer.submittedAt,
                    analysisStatus = feedback.analysisStatus,
                    analysis = feedback.analysis,
                    reviewed = feedback.teacherReview != null,
                    teacherReview = feedback.teacherReview,
                )
            }

        return ReviewTargetListResponse(
            roomId = roomId,
            totalCount = rows.size,
            reviewedCount = rows.count { it.reviewed },
            answers = rows,
        )
    }
}
