package kr.passmate.rating.service

import kr.passmate.common.config.PolicyProperties
import kr.passmate.rating.dto.RatingAvailability
import kr.passmate.rating.dto.RatingBlockedReason
import kr.passmate.rating.repository.RoomRatingRepository
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class RoomRatingQueryService(
    private val roomRatingRepository: RoomRatingRepository,
    private val policy: PolicyProperties,
) {

    /**
     * 평가 가능 여부. 막는 조건을 **급한 순서대로** 본다 —
     * 이미 평가한 사람에게 "기간이 지났어요"라고 하면 무슨 말인지 모른다.
     *
     * 평가 기간(`rating-window-hours`)은 정책값이라 env 로 바뀐다.
     */
    fun availability(room: Room, participantId: Long, hasSubmitted: Boolean): RatingAvailability {
        val alreadyRated = roomRatingRepository.existsByRoomIdAndParticipantId(room.id, participantId)
        val deadline = room.endedAt?.plusHours(policy.ratingWindowHours)

        val blocked = when {
            alreadyRated -> RatingBlockedReason.ALREADY_RATED
            room.status != RoomStatus.ENDED || deadline == null -> RatingBlockedReason.SESSION_NOT_ENDED
            // 답안을 한 개도 내지 않았으면 평가할 거리가 없다
            !hasSubmitted -> RatingBlockedReason.NO_SUBMISSION
            deadline.isBefore(LocalDateTime.now()) -> RatingBlockedReason.WINDOW_CLOSED
            else -> null
        }

        return RatingAvailability(
            available = blocked == null,
            blockedReason = blocked,
            alreadyRated = alreadyRated,
            deadline = deadline,
        )
    }
}
