package kr.passmate.rating.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.rating.domain.RatingTag
import kr.passmate.rating.domain.RoomRating
import java.time.LocalDateTime

/**
 * 평가 한 줄.
 *
 * **누가 남겼는지는 싣지 않는다.** 참가자 수가 적은 방에서 닉네임까지 붙으면 익명이 아니게 되고,
 * 낮은 별점을 남긴 학생이 호스트에게 그대로 드러난다. 호스트가 볼 것은 내용이지 누구인지가 아니다.
 */
@Schema(description = "세션 평가 한 건 (익명)")
data class RoomRatingResponse(
    val id: Long,
    val stars: Int,
    val tags: List<RatingTag>,
    val comment: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(rating: RoomRating) = RoomRatingResponse(
            id = rating.id,
            stars = rating.stars,
            tags = rating.tags ?: emptyList(),
            comment = rating.comment,
            createdAt = rating.createdAt,
        )
    }
}

@Schema(description = "태그별 선택 수")
data class RatingTagCount(
    val tag: RatingTag,
    @field:Schema(description = "화면에 그대로 쓰는 문구")
    val label: String,
    val count: Int,
)

/**
 * 방 하나에 남겨진 평가 전부와 집계(W-09 · M-13 · M-T3, FR-044). 호스트 본인만 본다.
 */
@Schema(description = "방 평가 목록·집계")
data class RoomRatingListResponse(
    val roomId: Long,
    @field:Schema(description = "평가가 없으면 null. 0.0 은 '별 0개'로 읽혀서 쓰지 않는다")
    val averageStars: Double?,
    val totalCount: Int,
    @field:Schema(description = "별점별 개수. 1~5 를 항상 다 채워 보내 막대그래프가 빈칸 없이 그려진다")
    val starCounts: Map<Int, Int>,
    @field:Schema(description = "많이 선택된 순. 0건인 태그는 빠진다")
    val tagCounts: List<RatingTagCount>,
    @field:Schema(description = "최근 순")
    val ratings: List<RoomRatingResponse>,
)
