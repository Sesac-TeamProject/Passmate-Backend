package kr.passmate.report.service

import kr.passmate.common.security.AuthPrincipal
import kr.passmate.feedback.dto.AnswerFeedbackView
import kr.passmate.feedback.service.AnswerFeedbackQueryService
import kr.passmate.question.domain.Question
import kr.passmate.rating.service.RoomRatingQueryService
import kr.passmate.report.dto.AnswerResultView
import kr.passmate.report.dto.MySessionResultResponse
import kr.passmate.report.dto.ParticipantResultResponse
import kr.passmate.report.dto.ParticipantResultRow
import kr.passmate.report.dto.QuestionResultRow
import kr.passmate.report.dto.ResultSummary
import kr.passmate.report.dto.SessionResultsResponse
import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.Room
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.domain.Answer
import kr.passmate.session.domain.SessionQuestion
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.session.service.SessionQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 세션 결과 조회 (FR-030 · FR-031 · FR-034).
 *
 * 세 화면(방 리포트·내 결과·학생별 상세)이 같은 재료를 다르게 자른다.
 * 그래서 방 단위 재료를 **한 번에 모아 두고**([Materials]) 각 응답을 거기서 만든다 —
 * 화면마다 따로 읽으면 참가자 수 × 문항 수만큼 쿼리가 늘어난다.
 */
@Service
@Transactional(readOnly = true)
class SessionResultQueryService(
    private val roomQueryService: RoomQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val sessionQueryService: SessionQueryService,
    private val answerQueryService: AnswerQueryService,
    private val answerFeedbackQueryService: AnswerFeedbackQueryService,
    private val roomRatingQueryService: RoomRatingQueryService,
) {

    /** 방 전체 통계. 학생별 점수가 통째로 나가므로 호스트만 본다. */
    fun sessionResults(roomId: Long, hostUserId: Long): SessionResultsResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        val m = gather(room)

        val analyzedByAnswer = answerFeedbackQueryService.viewsOf(m.answers.map { it.id })

        val questionRows = m.sessionQuestions.map { sq ->
            val answers = m.answersBySessionQuestion[sq.id].orEmpty()
            val graded = answers.filter { it.isCorrect != null }
            val correct = graded.count { it.isCorrect == true }
            val question = m.questionsById[sq.questionId]
            QuestionResultRow(
                sessionQuestionId = sq.id,
                questionId = sq.questionId,
                orderNo = sq.orderNo,
                type = question?.type ?: error("출제된 문항의 원본이 없습니다: ${sq.questionId}"),
                content = question.content,
                points = question.points,
                submitCount = answers.size,
                correctCount = correct,
                correctRate = percent(correct, graded.size),
                aiAnalysisCount = answers.count { analyzedByAnswer[it.id]?.analysis != null },
            )
        }

        val gradedAll = m.answers.filter { it.isCorrect != null }
        return SessionResultsResponse(
            roomId = roomId,
            title = room.title,
            status = room.status,
            startedAt = room.startedAt,
            endedAt = room.endedAt,
            summary = ResultSummary(
                participantCount = m.participants.size,
                questionCount = m.sessionQuestions.size,
                avgCorrectRate = percent(gradedAll.count { it.isCorrect == true }, gradedAll.size),
                // 한 문제도 안 푼 사람도 분모에 넣는다 — 참여율이 낮으면 평균도 낮게 보여야 한다
                avgScore = if (m.participants.isEmpty()) 0.0
                else m.participants.sumOf { m.scoreOf(it.id) }.toDouble() / m.participants.size,
                aiAnalysisCount = answerFeedbackQueryService.countAnalyzed(m.answers.map { it.id }),
            ),
            questions = questionRows,
            participants = m.participants.map { participant ->
                val answers = m.answersByParticipant[participant.id].orEmpty()
                ParticipantResultRow(
                    rank = m.rankOf(participant.id),
                    participantId = participant.id,
                    nickname = participant.nickname,
                    avatarId = participant.avatarId,
                    totalScore = m.scoreOf(participant.id),
                    correctCount = answers.count { it.isCorrect == true },
                    submitCount = answers.size,
                )
            }.sortedBy { it.rank },
        )
    }

    /** 내 결과. 게스트도 본다 — 자기 참가자 기록만 보이므로 남의 답안은 새지 않는다. */
    fun myResult(roomId: Long, principal: AuthPrincipal): MySessionResultResponse {
        val room = roomQueryService.getRoom(roomId)
        val participantId = answerQueryService.resolveParticipantId(roomId, principal)
        val participant = participantQueryService.getOfRoom(roomId, participantId)
        val m = gather(room)
        val answers = m.answersByParticipant[participantId].orEmpty()

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
            questions = answerViews(m, answers),
            rating = roomRatingQueryService.availability(room, participantId, hasSubmitted = answers.isNotEmpty()),
        )
    }

    /** 특정 학생의 문항별 답변·피드백·첨삭. 남의 답안이라 호스트만 본다. */
    fun participantResult(roomId: Long, participantId: Long, hostUserId: Long): ParticipantResultResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        val participant = participantQueryService.getOfRoom(roomId, participantId)
        val m = gather(room)
        val answers = m.answersByParticipant[participantId].orEmpty()

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
            questions = answerViews(m, answers),
        )
    }

    /**
     * 문항 순서대로 한 줄씩. **제출하지 않은 문항도 줄을 만든다** —
     * 빠뜨린 문제를 지우면 학생은 뭘 놓쳤는지 알 수 없다.
     */
    private fun answerViews(m: Materials, answers: List<Answer>): List<AnswerResultView> {
        val byQuestion = answers.associateBy { it.sessionQuestionId }
        val feedbacks = answerFeedbackQueryService.viewsOf(answers.map { it.id })

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
                // 마감 전에는 정답·해설을 내보내지 않는다 (QUESTION_STARTED 와 같은 원칙)
                answer = question.answer.takeIf { sq.isEnded },
                explanation = question.explanation?.takeIf { sq.isEnded },
                submitted = answer?.submitted,
                isCorrect = answer?.isCorrect,
                score = answer?.score ?: 0,
                finalScore = answer?.finalScore ?: 0,
                analysisStatus = feedback.analysisStatus,
                analysis = feedback.analysis,
                teacherReview = feedback.teacherReview,
            )
        }
    }

    private fun gather(room: Room): Materials {
        val answers = answerQueryService.listByRoom(room.id)
        return Materials(
            sessionQuestions = sessionQueryService.sessionQuestions(room.id),
            questionsById = sessionQueryService.questionsOf(room).associateBy { it.id },
            participants = participantQueryService.listAll(room.id),
            answers = answers,
        )
    }

    private fun percent(part: Int, whole: Int): Double =
        if (whole == 0) 0.0 else part * 100.0 / whole

    /**
     * 방 하나 분량의 결과 재료. 점수·순위는 답안에서 바로 계산한다 —
     * 이미 손에 들고 있는 값이라 랭킹을 따로 조회할 이유가 없고,
     * 중도 이탈자까지 같은 기준으로 줄을 세울 수 있다.
     */
    private class Materials(
        val sessionQuestions: List<SessionQuestion>,
        val questionsById: Map<Long, Question>,
        val participants: List<Participant>,
        val answers: List<Answer>,
    ) {
        val answersByParticipant: Map<Long, List<Answer>> = answers.groupBy { it.participantId }
        val answersBySessionQuestion: Map<Long, List<Answer>> = answers.groupBy { it.sessionQuestionId }

        /** 보정된 final_score 로 센다 — 서술형은 첨삭 뒤에 값이 바뀐다. */
        private val scores: Map<Long, Long> =
            answersByParticipant.mapValues { (_, list) -> list.sumOf { it.finalScore }.toLong() }

        /** 동점은 같은 등수로 묶는다(공동 3등 다음은 5등). 답안이 없는 사람은 0점 공동 꼴등. */
        private val ranks: Map<Long, Int> = participants
            .map { it.id to (scores[it.id] ?: 0L) }
            .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenBy { it.first })
            .let { rows ->
                var rank = 0
                var prev: Long? = null
                rows.mapIndexed { index, (participantId, score) ->
                    if (score != prev) {
                        rank = index + 1
                        prev = score
                    }
                    participantId to rank
                }
            }
            .toMap()

        fun scoreOf(participantId: Long): Long = scores[participantId] ?: 0L
        fun rankOf(participantId: Long): Int = ranks[participantId] ?: 0
    }
}
