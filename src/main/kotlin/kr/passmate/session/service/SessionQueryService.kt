package kr.passmate.session.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.domain.SessionQuestion
import kr.passmate.session.dto.QuestionResultResponse
import kr.passmate.session.dto.QuestionStartedPayload
import kr.passmate.session.dto.RankingEntry
import kr.passmate.session.dto.SessionSnapshotResponse
import kr.passmate.session.dto.SubmissionStatusPayload
import kr.passmate.session.repository.AnswerRepository
import kr.passmate.session.repository.RoomStateRepository
import kr.passmate.session.repository.SessionQuestionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SessionQueryService(
    private val roomQueryService: RoomQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val questionSetQueryService: QuestionSetQueryService,
    private val sessionQuestionRepository: SessionQuestionRepository,
    private val answerRepository: AnswerRepository,
    private val roomStateRepository: RoomStateRepository,
) {

    /**
     * 랭킹. 점수는 답안 집계에서, 닉네임은 room 기능에서 각각 가져와 합친다.
     * 참가자는 room 소유라 session 이 직접 조회하지 않는다.
     */
    fun ranking(roomId: Long): List<RankingEntry> {
        val participants = participantQueryService.listJoined(roomId).associateBy { it.id }
        return roomStateRepository.findRanking(roomId)
            .mapNotNull { score ->
                participants[score.participantId]?.let {
                    RankingEntry(0, it.id, it.nickname, it.avatarId, score.totalScore)
                }
            }
            // 동점은 같은 등수로 묶는다(공동 3등 다음은 5등)
            .let { rows ->
                var rank = 0
                var prev: Long? = null
                rows.mapIndexed { index, row ->
                    if (row.totalScore != prev) { rank = index + 1; prev = row.totalScore }
                    row.copy(rank = rank)
                }
            }
    }

    /** 호스트 전용 제출 현황. */
    fun submissionStatus(roomId: Long, hostUserId: Long): SubmissionStatusPayload {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        val current = currentQuestion(roomId) ?: throw BusinessException(ErrorCode.QUESTION_NOT_RUNNING)
        val stat = roomStateRepository.findSubmissionStat(current.id)
        return SubmissionStatusPayload(
            sessionQuestionId = current.id,
            submitCount = stat.submitCount,
            participantCount = room.participantCount,
            correctCount = stat.correctCount,
            correctRate = if (stat.submitCount == 0) 0.0 else stat.correctCount * 100.0 / stat.submitCount,
            distribution = stat.distribution,
        )
    }

    /** 방에서 지금 열려 있는 문항. 없으면 null. */
    fun currentQuestion(roomId: Long): SessionQuestion? =
        sessionQuestionRepository.findAllByRoomIdOrderByOrderNoAsc(roomId).firstOrNull { it.isRunning }

    fun sessionQuestions(roomId: Long): List<SessionQuestion> =
        sessionQuestionRepository.findAllByRoomIdOrderByOrderNoAsc(roomId)

    fun findSessionQuestion(roomId: Long, questionId: Long): SessionQuestion =
        sessionQuestionRepository.findByRoomIdAndQuestionId(roomId, questionId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND, "이 방에서 출제된 문항이 아닙니다.")

    /**
     * 재접속 복구용 스냅샷. 끊겼다 돌아온 참가자는 이걸 받아 현재 화면을 그대로 복원한다.
     * 진행 중 문항의 **정답은 포함하지 않는다** — QUESTION_STARTED 와 같은 원칙이다.
     */
    fun snapshot(roomId: Long, principal: AuthPrincipal): SessionSnapshotResponse {
        val room = roomQueryService.getRoom(roomId)
        val all = sessionQuestions(roomId)
        val current = all.firstOrNull { it.isRunning }

        val payload = current?.let { sq ->
            val question = room.questionSetId
                ?.let { setId -> questionSetQueryService.getDetail(setId, room.hostUserId).second }
                ?.firstOrNull { it.id == sq.questionId }
            question?.let {
                QuestionStartedPayload(
                    sessionQuestionId = sq.id,
                    questionId = it.id,
                    orderNo = sq.orderNo,
                    totalCount = all.size,
                    type = it.type,
                    content = it.content,
                    choices = it.choices,
                    points = it.points,
                    timeLimitSec = sq.timeLimitSec,
                    endsAt = requireNotNull(sq.endsAt),
                )
            }
        }

        val submitted = current?.let { sq ->
            runCatching { resolveParticipantId(roomId, principal) }.getOrNull()
                ?.let { hasSubmitted(it, sq.id) }
        } ?: false

        return SessionSnapshotResponse(
            roomId = roomId,
            status = room.status,
            currentQuestionNo = room.currentQuestionNo,
            totalCount = all.size,
            screenLocked = room.screenLocked,
            currentQuestion = payload,
            submitted = submitted,
            ranking = ranking(roomId),
        )
    }

    /** 마감된 문항의 결과. 아직 진행 중이면 정답이 새지 않게 막는다. */
    fun questionResult(roomId: Long, questionId: Long): QuestionResultResponse {
        val room = roomQueryService.getRoom(roomId)
        val sq = findSessionQuestion(roomId, questionId)
        if (!sq.isEnded) throw BusinessException(ErrorCode.QUESTION_NOT_RUNNING, "아직 마감되지 않은 문항입니다.")

        val question = room.questionSetId
            ?.let { questionSetQueryService.getDetail(it, room.hostUserId).second }
            ?.firstOrNull { it.id == questionId }

        return QuestionResultResponse(
            sessionQuestionId = sq.id,
            questionId = questionId,
            orderNo = sq.orderNo,
            answer = question?.answer,
            explanation = question?.explanation,
            submitCount = sq.submitCount,
            correctCount = sq.correctCount,
            correctRate = sq.correctRate?.toDouble() ?: 0.0,
            distribution = sq.answerDistribution.orEmpty(),
            ranking = ranking(roomId),
        )
    }

    private fun resolveParticipantId(roomId: Long, principal: AuthPrincipal): Long = when (principal) {
        is UserPrincipal -> participantQueryService.listJoined(roomId)
            .first { it.userId == principal.userId }.id
        is GuestPrincipal -> principal.participantId
    }

    fun hasSubmitted(participantId: Long, sessionQuestionId: Long): Boolean =
        answerRepository.existsByParticipantIdAndSessionQuestionId(participantId, sessionQuestionId)
}
