package kr.passmate.rating.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 평가할 수 없는 이유. 화면이 띄울 안내 문구가 이유마다 다르다. */
enum class RatingBlockedReason {
    /** 아직 세션이 끝나지 않았다 */
    SESSION_NOT_ENDED,

    /** 답안을 한 개도 내지 않았다 — "평가할 수 없어요" 토스트 */
    NO_SUBMISSION,

    /** 종료 후 평가 기간이 지났다 */
    WINDOW_CLOSED,

    /** 이미 평가했다 — 완료 표시 */
    ALREADY_RATED,
}

/**
 * 이 사람이 지금 이 방을 평가할 수 있는지 (FR-035 · FR-036).
 *
 * 조건이 여럿이라 boolean 하나로는 화면이 뭘 띄울지 못 정한다 — 막힌 이유를 함께 준다.
 */
@Schema(description = "세션 평가 가능 여부")
data class RatingAvailability(
    val available: Boolean,
    @field:Schema(description = "available=false 일 때만 채워진다")
    val blockedReason: RatingBlockedReason?,
    val alreadyRated: Boolean,
    @field:Schema(description = "평가 마감 시각. 세션이 끝나야 정해진다")
    val deadline: LocalDateTime?,
)
