package kr.passmate.session.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.question.domain.Question
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.room.domain.Room
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.domain.SessionQuestion
import kr.passmate.session.dto.QuestionResultResponse
import kr.passmate.session.dto.QuestionStartedPayload
import kr.passmate.session.dto.RankingEntry
import kr.passmate.session.dto.SessionSnapshotResponse
import kr.passmate.session.dto.SubmissionStatusPayload
import kr.passmate.session.repository.AnswerRepository
import kr.passmate.session.repository.ParticipantScore
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
    /** 랭킹 조회(REST) — 방에 속한 사람만. 내부 집계·브로드캐스트는 인가 없는 ranking(roomId) 을 그대로 쓴다. */
    fun ranking(roomId: Long, principal: AuthPrincipal): List<RankingEntry> {
        participantQueryService.verifyBelongsToRoom(roomQueryService.getRoom(roomId), principal)
        return ranking(roomId)
    }

    fun ranking(roomId: Long): List<RankingEntry> =
        toEntries(roomId, roomStateRepository.findRanking(roomId))

    /**
     * [orderNo] 번 문항 마감 시점의 랭킹 + **직전 문항 대비 순위 변동**(웹 QA_BACKLOG B-17).
     *
     * 변동은 직전 시점에도 점수가 있던 참가자만 낸다 — 1번 문항이거나 그때 답안이 없던 사람은 null 이다.
     * 0 으로 채우면 화면이 "변동 없음"으로 그려 실제로 추적한 값처럼 보인다.
     */
    fun rankingAsOf(roomId: Long, orderNo: Int): List<RankingEntry> {
        val current = toEntries(roomId, roomStateRepository.findRankingAsOf(roomId, orderNo))
        val previousRanks = toEntries(roomId, roomStateRepository.findRankingAsOf(roomId, orderNo - 1))
            .associate { it.participantId to it.rank }

        return current.map { entry ->
            entry.copy(rankChange = previousRanks[entry.participantId]?.let { it - entry.rank })
        }
    }

    /** 점수 목록에 닉네임을 붙이고 등수를 매긴다. 동점은 같은 등수로 묶는다(공동 3등 다음은 5등). */
    private fun toEntries(roomId: Long, scores: List<ParticipantScore>): List<RankingEntry> {
        val participants = participantQueryService.listJoined(roomId).associateBy { it.id }
        return scores
            .mapNotNull { score ->
                participants[score.participantId]?.let {
                    RankingEntry(0, it.id, it.nickname, it.avatarId, score.totalScore)
                }
            }
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

    /**
     * [sq] 의 정답률이 **직전에 마감된 문항**보다 얼마나 오르내렸는지(%p, 웹 QA_BACKLOG B-17).
     *
     * 호스트가 문항을 건너뛸 수 있어 orderNo - 1 이 아니라 "앞쪽에서 마감된 것 중 가장 뒤"와 견준다.
     * 견줄 문항이 없으면 null 이다 — 0 은 "변동 없음"이라는 뜻이라 구분되어야 한다.
     */
    fun accuracyDeltaOf(sq: SessionQuestion): Double? {
        val previous = sessionQuestions(sq.roomId)
            .filter { it.orderNo < sq.orderNo && it.isEnded }
            .maxByOrNull { it.orderNo }
            ?: return null
        return (sq.correctRate ?: return null).toDouble() - (previous.correctRate ?: return null).toDouble()
    }

    fun findSessionQuestion(roomId: Long, questionId: Long): SessionQuestion =
        sessionQuestionRepository.findByRoomIdAndQuestionId(roomId, questionId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND, "이 방에서 출제된 문항이 아닙니다.")

    /**
     * 방에 출제된 문항 원본 전부. **정답·해설이 들어 있다** — 언제 내보낼지는 호출자가 판단한다.
     * 세트는 호스트 소유라 세션 문맥에서만 호스트 자격으로 꺼낸다.
     *
     * 결과·리포트는 문항마다 원본이 필요하다. 한 건씩 찾으면 문항 수만큼 세트를 다시 읽는다.
     */
    fun questionsOf(room: Room): List<Question> =
        room.questionSetId
            ?.let { setId -> questionSetQueryService.getDetail(setId, room.hostUserId).second }
            .orEmpty()

    fun findQuestion(room: Room, questionId: Long): Question? =
        questionsOf(room).firstOrNull { it.id == questionId }

    /**
     * 재접속 복구용 스냅샷. 끊겼다 돌아온 참가자는 이걸 받아 현재 화면을 그대로 복원한다.
     * 진행 중 문항의 **정답은 포함하지 않는다** — QUESTION_STARTED 와 같은 원칙이다.
     */
    fun snapshot(roomId: Long, principal: AuthPrincipal): SessionSnapshotResponse {
        val room = roomQueryService.getRoom(roomId)
        participantQueryService.verifyBelongsToRoom(room, principal)
        val all = sessionQuestions(roomId)
        val current = all.firstOrNull { it.isRunning }

        val payload = current?.let { sq ->
            findQuestion(room, sq.questionId)?.let {
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
    fun questionResult(roomId: Long, questionId: Long, principal: AuthPrincipal): QuestionResultResponse {
        val room = roomQueryService.getRoom(roomId)
        participantQueryService.verifyBelongsToRoom(room, principal)
        val sq = findSessionQuestion(roomId, questionId)
        if (!sq.isEnded) throw BusinessException(ErrorCode.QUESTION_NOT_RUNNING, "아직 마감되지 않은 문항입니다.")

        val question = findQuestion(room, questionId)

        return QuestionResultResponse(
            sessionQuestionId = sq.id,
            questionId = questionId,
            orderNo = sq.orderNo,
            answer = question?.answer,
            explanation = question?.explanation,
            submitCount = sq.submitCount,
            correctCount = sq.correctCount,
            correctRate = sq.correctRate?.toDouble() ?: 0.0,
            accuracyDelta = accuracyDeltaOf(sq),
            distribution = sq.answerDistribution.orEmpty(),
            ranking = rankingAsOf(roomId, sq.orderNo),
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
