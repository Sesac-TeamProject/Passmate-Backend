package kr.passmate.session.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.session.domain.Answer
import kr.passmate.session.repository.AnswerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 답안 조회 창구. answer 는 session 소유라 다른 기능(feedback·report)은 여기를 통해서만 본다.
 */
@Service
@Transactional(readOnly = true)
class AnswerQueryService(
    private val sessionQueryService: SessionQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val answerRepository: AnswerRepository,
) {

    /** 내가 그 문항에 낸 답안. 아직 안 냈으면 null. */
    fun findMyAnswer(roomId: Long, questionId: Long, principal: AuthPrincipal): Answer? {
        val sq = sessionQueryService.findSessionQuestion(roomId, questionId)
        val participantId = resolveParticipantId(roomId, principal)
        return answerRepository.findByParticipantIdAndSessionQuestionId(participantId, sq.id)
    }

    /** 내 답안. 없으면 404 — 분석처럼 답안이 있어야만 되는 경로에서 쓴다. */
    fun getMyAnswer(roomId: Long, questionId: Long, principal: AuthPrincipal): Answer =
        findMyAnswer(roomId, questionId, principal)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "아직 제출한 답안이 없습니다.")

    fun getAnswer(answerId: Long): Answer =
        answerRepository.findById(answerId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND, "답안을 찾을 수 없습니다.") }

    /** 회원은 계정으로, 게스트는 토큰에 담긴 참가자 id 로 자기 자신을 찾는다. */
    private fun resolveParticipantId(roomId: Long, principal: AuthPrincipal): Long = when (principal) {
        is UserPrincipal -> participantQueryService.listJoined(roomId)
            .firstOrNull { it.userId == principal.userId }?.id
            ?: throw BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND, "이 방에 참여한 기록이 없습니다.")

        is GuestPrincipal -> {
            // 게스트 토큰은 자기가 입장한 방 하나에만 쓸 수 있다
            if (principal.roomId != roomId) throw BusinessException(ErrorCode.ACCESS_DENIED)
            principal.participantId
        }
    }
}
