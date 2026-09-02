package kr.passmate.hostlevel.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 뱃지 한 칸 (M-09 뱃지 컬렉션). 못 딴 것도 진행도와 함께 내려간다 —
 * 화면이 "30일 연속 활동 12/30" 을 그려야 한다.
 */
@Schema(description = "뱃지 한 칸")
data class BadgeResponse(
    val code: String,
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val achieved: Boolean,
    val achievedAt: LocalDateTime?,
    val progress: Int,
    @field:Schema(description = "달성 목표치. 조건이 없는 뱃지는 null")
    val target: Double?,
)

@Schema(description = "뱃지 컬렉션과 획득 이력")
data class BadgeCollectionResponse(
    val achievedCount: Int,
    val totalCount: Int,
    @field:Schema(description = "획득한 것 먼저, 그 안에서는 최근 획득 순")
    val badges: List<BadgeResponse>,
)
