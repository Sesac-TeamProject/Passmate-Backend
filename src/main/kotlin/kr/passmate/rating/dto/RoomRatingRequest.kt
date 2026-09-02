package kr.passmate.rating.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import kr.passmate.rating.domain.RatingTag
import kr.passmate.rating.domain.RoomRating

/**
 * 세션 평가 제출(M-06 v2). 별점만 필수고 태그·후기는 선택이다.
 *
 * 한 번 내면 수정할 수 없으므로(FR-043) PUT 이 아니라 POST 다.
 */
@Schema(description = "세션 평가 제출")
data class RoomRatingRequest(
    @field:Schema(description = "1~5", example = "5")
    @field:Min(RoomRating.STARS_MIN.toLong())
    @field:Max(RoomRating.STARS_MAX.toLong())
    val stars: Int,

    @field:Schema(description = "다중 선택 태그. 중복은 서버가 정리한다")
    @field:Size(max = 5, message = "태그는 5개까지 고를 수 있습니다")
    val tags: List<RatingTag>? = null,

    @field:Schema(description = "한 줄 후기. 호스트에게만 보인다")
    @field:Size(max = RoomRating.COMMENT_MAX)
    val comment: String? = null,
)
