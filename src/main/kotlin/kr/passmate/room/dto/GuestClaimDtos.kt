package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kr.passmate.room.domain.Participant
import java.time.LocalDateTime

/**
 * 게스트 기록 전환 (FR-036, M-05 "가입하고 기록 저장하기").
 *
 * 입장할 때 받아 둔 `guestToken` 을 그대로 제출한다 — 게스트 JWT 가 아니라
 * 참가자에 박혀 있는 값이다. JWT 는 만료되지만 이 값은 보관 기한까지 살아 있다.
 */
@Schema(description = "게스트 기록 계정 연동")
data class GuestClaimRequest(
    @field:Schema(description = "입장 시 받은 게스트 토큰")
    @field:NotBlank(message = "게스트 토큰은 필수입니다.")
    @field:Size(max = 64)
    val guestToken: String,
)

@Schema(description = "계정에 연동된 세션 기록")
data class GuestClaimResponse(
    val roomId: Long,
    val roomTitle: String,
    val participantId: Long,
    @field:Schema(description = "그 세션에서 쓰던 닉네임. 연동해도 바꾸지 않는다")
    val nickname: String,
    val totalScore: Int,
    val finalRank: Int?,
    val claimedAt: LocalDateTime?,
) {
    companion object {
        fun of(participant: Participant, roomTitle: String) = GuestClaimResponse(
            roomId = participant.roomId,
            roomTitle = roomTitle,
            participantId = participant.id,
            nickname = participant.nickname,
            totalScore = participant.totalScore,
            finalRank = participant.finalRank,
            claimedAt = participant.claimedAt,
        )
    }
}
