package kr.passmate.room.service

import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.room.dto.GuestClaimRequest
import kr.passmate.room.dto.GuestClaimResponse
import kr.passmate.room.repository.ParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 게스트 기록의 계정 연동 (FR-036, US9 · M-05).
 *
 * 참가자 행의 주인만 바꾼다 — 답안·리포트·평가는 전부 participant_id 를 가리키므로
 * 따로 옮길 것이 없다. 옮기려 들면 오히려 id 가 갈라져 결과 화면이 깨진다.
 */
@Service
class GuestClaimService(
    private val participantRepository: ParticipantRepository,
    private val roomQueryService: RoomQueryService,
    private val policy: PolicyProperties,
) {

    @Transactional
    fun claim(userId: Long, request: GuestClaimRequest, now: LocalDateTime = LocalDateTime.now()): GuestClaimResponse {
        val participant = participantRepository.findByGuestToken(request.guestToken.trim())
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "연동할 기록을 찾을 수 없습니다.")

        if (participant.claimedAt != null) throw BusinessException(ErrorCode.GUEST_RECORD_ALREADY_CLAIMED)

        val room = roomQueryService.getRoom(participant.roomId)

        // 보관 기한은 세션이 끝난 때부터 센다. 아직 안 끝난 세션은 기한을 따질 것이 없다
        room.endedAt?.let { endedAt ->
            if (endedAt.plusDays(policy.guestRetentionDays).isBefore(now)) {
                throw BusinessException(ErrorCode.GUEST_RECORD_EXPIRED)
            }
        }

        // 회원으로도 같은 방에 들어갔었다면 연동할 수 없다 — 한 사람이 한 방에 두 줄로 남으면
        // 결과·랭킹에서 같은 사람이 두 번 세어진다
        if (participantRepository.existsByRoomIdAndUserId(participant.roomId, userId)) {
            throw BusinessException(
                ErrorCode.CONFLICT,
                "이미 회원으로 참여한 방입니다.",
            )
        }

        participant.claim(userId, now)
        return GuestClaimResponse.of(participant, room.title)
    }
}
