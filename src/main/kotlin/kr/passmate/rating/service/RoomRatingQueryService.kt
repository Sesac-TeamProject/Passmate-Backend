package kr.passmate.rating.service

import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.rating.domain.RatingTag
import kr.passmate.rating.domain.RoomRating
import kr.passmate.rating.dto.RatingAvailability
import kr.passmate.rating.dto.RatingBlockedReason
import kr.passmate.rating.dto.RatingTagCount
import kr.passmate.rating.dto.RoomRatingListResponse
import kr.passmate.rating.dto.RoomRatingResponse
import kr.passmate.rating.repository.RoomRatingRepository
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.service.RoomQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class RoomRatingQueryService(
    private val roomRatingRepository: RoomRatingRepository,
    private val roomQueryService: RoomQueryService,
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

    /**
     * 방에 남겨진 평가와 집계. **호스트 본인만** 볼 수 있다 — 한 줄 후기는 호스트에게만 공개다.
     */
    fun listOfRoom(roomId: Long, hostUserId: Long): RoomRatingListResponse {
        val room = roomQueryService.getRoom(roomId)
        if (room.hostUserId != hostUserId) throw BusinessException(ErrorCode.NOT_ROOM_HOST)

        val ratings = roomRatingRepository.findAllByRoomIdOrderByIdDesc(roomId)
        return RoomRatingListResponse(
            roomId = roomId,
            averageStars = ratings.map { it.stars }.averageOrNull(),
            totalCount = ratings.size,
            starCounts = starCounts(ratings),
            tagCounts = tagCounts(ratings),
            ratings = ratings.map(RoomRatingResponse::from),
        )
    }

    /**
     * 이 호스트가 받은 평가를 방별로 묶은 요약. 방 목록 화면이 방마다 조회하지 않도록
     * 한 번에 읽어 나눈다.
     */
    fun starsOfHost(hostUserId: Long): HostRatingSummary {
        val ratings = roomRatingRepository.findAllByHostUserId(hostUserId)
        return HostRatingSummary(
            overallAverage = ratings.map { it.stars }.averageOrNull(),
            countByRoom = ratings.groupingBy { it.roomId }.eachCount(),
            averageByRoom = ratings.groupBy { it.roomId }
                .mapValues { (_, rows) -> rows.map { it.stars }.averageOrNull() ?: 0.0 },
            totalCount = ratings.size,
        )
    }

    /** 1~5 를 항상 다 채운다. 없는 별점이 빠지면 화면이 막대 자리를 잃는다. */
    private fun starCounts(ratings: List<RoomRating>): Map<Int, Int> {
        val counted = ratings.groupingBy { it.stars }.eachCount()
        return (RoomRating.STARS_MIN..RoomRating.STARS_MAX).associateWith { counted[it] ?: 0 }
    }

    /** 많이 고른 순. 0건인 태그는 화면에 띄울 것이 없어 빼고 보낸다. */
    private fun tagCounts(ratings: List<RoomRating>): List<RatingTagCount> =
        ratings.flatMap { it.tags ?: emptyList() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<RatingTag, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { RatingTagCount(it.key, it.key.label, it.value) }

    /** 평가가 없으면 0.0 이 아니라 null 이다 — 0.0 은 "별 0개"로 읽힌다. */
    private fun List<Int>.averageOrNull(): Double? =
        if (isEmpty()) null else Math.round(average() * 100.0) / 100.0
}

/** 호스트가 받은 평가 요약. 전체 평균과 방별 평균을 함께 준다. */
data class HostRatingSummary(
    val overallAverage: Double?,
    val averageByRoom: Map<Long, Double>,
    val countByRoom: Map<Long, Int>,
    val totalCount: Int,
)
