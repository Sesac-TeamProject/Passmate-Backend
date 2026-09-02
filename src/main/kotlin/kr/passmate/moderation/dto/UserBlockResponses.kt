package kr.passmate.moderation.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "차단한 호스트 한 줄")
data class BlockedUserResponse(
    val userId: Long,
    val nickname: String,
    @field:Schema(description = "차단 당시가 아니라 지금 등급")
    val level: Int,
    val blockedAt: LocalDateTime,
)

@Schema(description = "내가 차단한 호스트 목록 — 마이페이지 설정의 해제 진입점")
data class BlockedUsersResponse(
    val totalCount: Int,
    @field:Schema(description = "최근 차단한 순")
    val blocks: List<BlockedUserResponse>,
)
