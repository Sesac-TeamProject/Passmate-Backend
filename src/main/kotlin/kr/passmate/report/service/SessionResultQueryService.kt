package kr.passmate.report.service

import kr.passmate.common.security.AuthPrincipal
import kr.passmate.feedback.dto.AnswerFeedbackView
import kr.passmate.feedback.service.AnswerFeedbackQueryService
import kr.passmate.feedback.service.QuestionCommentQueryService
import kr.passmate.question.domain.QuestionType
import kr.passmate.rating.service.RoomRatingQueryService
import kr.passmate.report.dto.AnswerResultView
import kr.passmate.report.dto.EssayAiInsight
import kr.passmate.report.dto.EssayGradingCounts
import kr.passmate.report.dto.MySessionResultResponse
import kr.passmate.report.dto.ParticipantResultResponse
import kr.passmate.report.dto.ParticipantResultRow
import kr.passmate.report.dto.QuestionResultRow
import kr.passmate.report.dto.ResultSummary
import kr.passmate.report.dto.SessionResultsResponse
import kr.passmate.room.domain.Room
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.domain.Answer
import kr.passmate.session.service.AnswerQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 세션 결과 조회 (FR-030 · FR-031 · FR-034).
 *
 * 세 화면(방 리포트·내 결과·학생별 상세)이 같은 재료를 다르게 자른다.
 * 그래서 방 단위 재료를 **한 번에 모아 두고**([SessionMaterials]) 각 응답을 거기서 만든다 —
 * 화면마다 따로 읽으면 참가자 수 × 문항 수만큼 쿼리가 늘어난다.
 */
@Service
@Transactional(readOnly = true)
class SessionResultQueryService(
    private val roomQueryService: RoomQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val answerQueryService: AnswerQueryService,
    private val materialsLoader: SessionMaterialsLoader,
    private val answerFeedbackQueryService: AnswerFeedbackQueryService,
    private val questionCommentQueryService: QuestionCommentQueryService,
    private val roomRatingQueryService: RoomRatingQueryService,
) {

    /** 방 전체 통계. 학생별 점수가 통째로 나가므로 호스트만 본다. */
    fun sessionResults(roomId: Long, hostUserId: Long): SessionResultsResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        val m = materialsLoader.load(room)

        val analyzedByAnswer = answerFeedbackQueryService.viewsOf(m.answers.map { it.id })
        val comments = questionCommentQueryService.commentsOf(m.sessionQuestions.map { it.id })

        val questionRows = m.sessionQuestions.map { sq ->
            val answers = m.answersBySessionQuestion[sq.id].orEmpty()
            val question = m.questionsById[sq.questionId]
            QuestionResultRow(
                sessionQuestionId = sq.id,
                questionId = sq.questionId,
                orderNo = sq.orderNo,
                type = question?.type ?: error("출제된 문항의 원본이 없습니다: ${sq.questionId}"),
                content = question.content,
                points = question.points,
                submitCount = answers.size,
                correctCount = answers.count { it.isCorrect == true },
                correctRate = m.correctRateOf(sq.id),
                aiAnalysisCount = answers.count { analyzedByAnswer[it.id]?.analysis != null },
                teacherComment = comments[sq.id],
                // 끝난 방을 호스트만 보는 화면이라 정답·해설을 그대로 싣는다 — 우측 패널이 해설 칸을 그린다
                answer = question.answer,
                explanation = question.explanation,
                essayGrading = if (question.type == QuestionType.ESSAY) essayGrading(answers, question.points, analyzedByAnswer) else null,
                aiInsight = if (question.type == QuestionType.ESSAY) essayAiInsight(answers, analyzedByAnswer) else null,
            )
        }

        return SessionResultsResponse(
            roomId = roomId,
            title = room.title,
            status = room.status,
            startedAt = room.startedAt,
            endedAt = room.endedAt,
            summary = ResultSummary(
                participantCount = m.participants.size,
                questionCount = m.sessionQuestions.size,
                // 참가자 전원 × 자동 채점 문항이 분모 — 미제출도 오답, 서술형은 집계 제외
                // (제출·채점된 답안만 분모로 쓰면 안 푼 문제가 많을수록 값이 부풀었다 — 2026-09-08)
                avgCorrectRate = SessionMaterials.percent(
                    m.answers.count { it.isCorrect == true },
                    m.participants.size * m.gradableQuestionCount,
                ),
                // 한 문제도 안 푼 사람도 분모에 넣는다 — 참여율이 낮으면 평균도 낮게 보여야 한다
                avgScore = if (m.participants.isEmpty()) 0.0
                else m.participants.sumOf { m.scoreOf(it.id) }.toDouble() / m.participants.size,
                aiAnalysisCount = answerFeedbackQueryService.countAnalyzed(m.answers.map { it.id }),
                submittedParticipantCount = m.submittedParticipantCount,
                completionRate = m.completionRate,
                avgElapsedMs = m.avgElapsedMs,
                essayAnswerCount = m.essayAnswers.size,
                // "채점됨"의 기준은 선생님 첨삭 — AI 분석은 참고 자료일 뿐 점수를 확정하지 않는다
                essayReviewedCount = m.essayAnswers.count { analyzedByAnswer[it.id]?.teacherReview != null },
            ),
            questions = questionRows,
            participants = m.participants.map { participant ->
                ParticipantResultRow(
                    rank = m.rankOf(participant.id),
                    participantId = participant.id,
                    nickname = participant.nickname,
                    avatarId = participant.avatarId,
                    totalScore = m.scoreOf(participant.id),
                    correctCount = m.correctCountOf(participant.id),
                    submitCount = m.answersOf(participant.id).size,
                )
            }.sortedBy { it.rank },
        )
    }

    /** 내 결과. 게스트도 본다 — 자기 참가자 기록만 보이므로 남의 답안은 새지 않는다. */
    fun myResult(roomId: Long, principal: AuthPrincipal): MySessionResultResponse {
        val room = roomQueryService.getRoom(roomId)
        val participantId = answerQueryService.resolveParticipantId(roomId, principal)
        val participant = participantQueryService.getOfRoom(roomId, participantId)
        val m = materialsLoader.load(room)
        val answers = m.answersOf(participantId)

        return MySessionResultResponse(
            roomId = roomId,
            roomTitle = room.title,
            status = room.status,
            endedAt = room.endedAt,
            participantId = participantId,
            nickname = participant.nickname,
            avatarId = participant.avatarId,
            guest = participant.isGuest,
            rank = m.rankOf(participantId),
            totalScore = m.scoreOf(participantId),
            correctCount = answers.count { it.isCorrect == true },
            submitCount = answers.size,
            questionCount = m.sessionQuestions.size,
            participantCount = m.participants.size,
            elapsedMs = m.elapsedMsOf(participantId),
            questions = answerViews(m, answers),
            rating = roomRatingQueryService.availability(room, participantId, hasSubmitted = answers.isNotEmpty()),
        )
    }

    /** 특정 학생의 문항별 답변·피드백·첨삭. 남의 답안이라 호스트만 본다. */
    fun participantResult(roomId: Long, participantId: Long, hostUserId: Long): ParticipantResultResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        val participant = participantQueryService.getOfRoom(roomId, participantId)
        val m = materialsLoader.load(room)
        val answers = m.answersOf(participantId)

        return ParticipantResultResponse(
            roomId = roomId,
            participantId = participantId,
            nickname = participant.nickname,
            avatarId = participant.avatarId,
            rank = m.rankOf(participantId),
            totalScore = m.scoreOf(participantId),
            correctCount = answers.count { it.isCorrect == true },
            submitCount = answers.size,
            questionCount = m.sessionQuestions.size,
            participantCount = m.participants.size,
            elapsedMs = m.elapsedMsOf(participantId),
            questions = answerViews(m, answers),
        )
    }

    /**
     * 서술형 채점 분포 — 첨삭 점수를 배점과 견줘 만점·부분·0점으로 가른다.
     * 첨삭 전 답안은 최종 점수가 0 이지만 "오답"이 아니다 — 섞어 세면 채점이 안 끝난 문항이 전원 오답으로 보인다.
     */
    private fun essayGrading(
        answers: List<Answer>,
        points: Int,
        feedbacks: Map<Long, AnswerFeedbackView>,
    ): EssayGradingCounts {
        var full = 0
        var partial = 0
        var zero = 0
        var unreviewed = 0
        for (answer in answers) {
            val review = feedbacks[answer.id]?.teacherReview
            if (review == null) {
                unreviewed++
                continue
            }
            // 첨삭이 준 점수가 기준이다. 점수 없이 코멘트만 남긴 첨삭은 최종 점수(0)로 떨어진다
            val score = review.adjustedScore ?: answer.finalScore
            when {
                score >= points -> full++
                score > 0 -> partial++
                else -> zero++
            }
        }
        return EssayGradingCounts(full = full, partial = partial, zero = zero, unreviewed = unreviewed)
    }

    /**
     * 답안마다 흩어진 AI 분석을 문항 단위로 모은다 — 우측 패널 "AI 분석(참고 의견)".
     * 같은 문장이 여러 답안에서 반복되면 그게 이 문항의 공통 강점·공통 누락이다. 분석이 없으면 null.
     */
    private fun essayAiInsight(answers: List<Answer>, feedbacks: Map<Long, AnswerFeedbackView>): EssayAiInsight? {
        val analyses = answers.mapNotNull { feedbacks[it.id]?.analysis }
        if (analyses.isEmpty()) return null
        return EssayAiInsight(
            analyzedCount = analyses.size,
            commonKeyPoints = topByFrequency(analyses.flatMap { it.keyPoints }),
            commonMissingPoints = topByFrequency(analyses.flatMap { it.missingPoints }),
        )
    }

    /** 빈도순 상위 [INSIGHT_TOP_N]. 동률이면 먼저 나온 순서를 지킨다 */
    private fun topByFrequency(items: List<String>): List<String> =
        items.map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(INSIGHT_TOP_N)
            .map { it.key }

    /**
     * 문항 순서대로 한 줄씩. **제출하지 않은 문항도 줄을 만든다** —
     * 빠뜨린 문제를 지우면 학생은 뭘 놓쳤는지 알 수 없다.
     */
    private fun answerViews(m: SessionMaterials, answers: List<Answer>): List<AnswerResultView> {
        val byQuestion = answers.associateBy { it.sessionQuestionId }
        val feedbacks = answerFeedbackQueryService.viewsOf(answers.map { it.id })
        val comments = questionCommentQueryService.commentsOf(m.sessionQuestions.map { it.id })

        return m.sessionQuestions.map { sq ->
            val answer = byQuestion[sq.id]
            val question = m.questionsById.getValue(sq.questionId)
            val feedback = answer?.let { feedbacks[it.id] } ?: AnswerFeedbackView.NONE
            AnswerResultView(
                sessionQuestionId = sq.id,
                questionId = sq.questionId,
                orderNo = sq.orderNo,
                type = question.type,
                content = question.content,
                points = question.points,
                topic = question.topic,
                // 마감 전에는 정답·해설·반 정답률을 내보내지 않는다 (QUESTION_STARTED 와 같은 원칙)
                answer = question.answer.takeIf { sq.isEnded },
                explanation = question.explanation?.takeIf { sq.isEnded },
                submitted = answer?.submitted,
                isCorrect = answer?.isCorrect,
                score = answer?.score ?: 0,
                finalScore = answer?.finalScore ?: 0,
                correctRate = m.correctRateOf(sq.id).takeIf { sq.isEnded },
                elapsedMs = answer?.let { m.elapsedMsOf(it) },
                analysisStatus = feedback.analysisStatus,
                analysis = feedback.analysis,
                teacherReview = feedback.teacherReview,
                teacherComment = comments[sq.id],
            )
        }
    }

    private companion object {
        /** 우측 패널에 담는 공통 강점·누락 수 — 시안(784:8983)은 항목 두세 줄이다 */
        const val INSIGHT_TOP_N = 3
    }
}
