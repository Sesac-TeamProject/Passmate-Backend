package kr.passmate.session.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.room.service.ParticipantService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.scoring.service.ScoreCalculator
import kr.passmate.session.domain.Answer
import kr.passmate.session.domain.SessionEventType
import kr.passmate.session.repository.AnswerRepository
import kr.passmate.session.repository.RoomStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AnswerService(
    private val roomQueryService: RoomQueryService,
    private val participantService: ParticipantService,
    private val questionSetQueryService: QuestionSetQueryService,
    private val sessionQueryService: SessionQueryService,
    private val answerRepository: AnswerRepository,
    private val roomStateRepository: RoomStateRepository,
    private val scoreCalculator: ScoreCalculator,
    private val eventPublisher: SessionEventPublisher,
) {

    /**
     * 답안을 낸다.
     *
     * 제출 시각은 **서버가 받은 시각**만 쓴다. 클라이언트가 보낸 시각을 믿으면 속도 보너스를 조작할 수 있다.
     * 마감된 문항은 받지 않는다 — 타이머가 아직 안 돌았어도 endsAt 을 넘겼으면 거부한다.
     */
    @Transactional
    fun submit(roomId: Long, principal: AuthPrincipal, questionId: Long, submitted: String): Answer {
        val now = LocalDateTime.now()
        val room = roomQueryService.getRoom(roomId)
        room.verifyRunning()

        val sq = sessionQueryService.findSessionQuestion(roomId, questionId)
        if (!sq.isRunning || sq.isExpired(now)) {
            throw BusinessException(ErrorCode.QUESTION_NOT_RUNNING)
        }

        val participant = resolveParticipant(roomId, principal)
        if (answerRepository.existsByParticipantIdAndSessionQuestionId(participant, sq.id)) {
            throw BusinessException(ErrorCode.ALREADY_SUBMITTED)
        }

        val setId = room.questionSetId ?: throw BusinessException(ErrorCode.QUESTION_SET_REQUIRED)
        val question = questionSetQueryService.getDetail(setId, room.hostUserId).second
            .firstOrNull { it.id == questionId }
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND)

        val remainingRatio = sq.remainingRatio(now)
        val result = scoreCalculator.score(
            type = question.type,
            points = question.points,
            submitted = submitted,
            answer = question.answer,
            remainingRatio = remainingRatio,
        )

        val answer = answerRepository.save(
            Answer(
                participantId = participant,
                sessionQuestionId = sq.id,
                submitted = submitted,
                submittedAt = now,
            ).apply { applyScore(result.isCorrect, remainingRatio, result.baseScore, result.speedBonus) },
        )
        answerRepository.flush()

        // 제출 현황은 호스트만 본다. 학생에게 실시간 정답률이 보이면 눈치싸움이 된다
        publishSubmissionStatus(roomId, sq.id, room.participantCount)
        return answer
    }

    private fun publishSubmissionStatus(roomId: Long, sessionQuestionId: Long, participantCount: Int) {
        val stat = roomStateRepository.findSubmissionStat(sessionQuestionId)
        eventPublisher.toHost(
            roomId,
            SessionEventType.SUBMISSION_UPDATED,
            kr.passmate.session.dto.SubmissionStatusPayload(
                sessionQuestionId = sessionQuestionId,
                submitCount = stat.submitCount,
                participantCount = participantCount,
                correctCount = stat.correctCount,
                correctRate = if (stat.submitCount == 0) 0.0 else stat.correctCount * 100.0 / stat.submitCount,
                distribution = stat.distribution,
            ),
        )
    }

    /** 회원은 계정으로, 게스트는 토큰에 담긴 참가자 id 로 자기 자신을 찾는다. */
    private fun resolveParticipant(roomId: Long, principal: AuthPrincipal): Long = when (principal) {
        is UserPrincipal -> participantService.getJoinedParticipantOfUser(roomId, principal.userId).id
        is GuestPrincipal -> {
            if (principal.roomId != roomId) throw BusinessException(ErrorCode.ACCESS_DENIED)
            participantService.getParticipant(principal.participantId).id
        }
    }
}
