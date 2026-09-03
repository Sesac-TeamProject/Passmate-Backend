package kr.passmate.settlement.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.settlement.domain.HostEarning
import kr.passmate.settlement.domain.HostEarningStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "세션 한 건의 적립")
data class HostEarningRow(
    val roomId: Long,
    val roomTitle: String,
    val participantCount: Int,
    @field:Schema(description = "참가비 총액(코인, 1 C = ₩1)")
    val gross: Int,
    @field:Schema(description = "플랫폼 수수료 20%")
    val platformFee: Int,
    @field:Schema(description = "호스트 정산액 80%")
    val net: Int,
    val status: HostEarningStatus,
    val earnedAt: LocalDateTime,
) {
    companion object {
        fun of(earning: HostEarning, roomTitle: String) = HostEarningRow(
            roomId = earning.roomId,
            roomTitle = roomTitle,
            participantCount = earning.participantCount,
            gross = earning.gross,
            platformFee = earning.platformFee,
            net = earning.net,
            status = earning.status,
            earnedAt = earning.earnedAt,
        )
    }
}

/**
 * 내 수익·정산 내역 (FR-056, W-10 · M-T4).
 */
@Schema(description = "내 수익·정산 내역")
data class HostEarningsResponse(
    @field:Schema(description = "이번 달에 적립된 정산액 합계")
    val thisMonthNet: Int,
    @field:Schema(description = "아직 지급되지 않은 정산액 합계(이월 포함)")
    val pendingNet: Int,
    @field:Schema(description = "다음 지급 예정일")
    val nextPayoutDate: LocalDate,
    @field:Schema(description = "정산 계좌를 등록했는지. false 면 지급이 보류된다")
    val accountRegistered: Boolean,
    @field:Schema(description = "세션별 적립. 최근 순")
    val earnings: List<HostEarningRow>,
)
