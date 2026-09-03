package kr.passmate.coin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import kr.passmate.coin.domain.CoinCharge
import kr.passmate.coin.domain.CoinChargeStatus
import kr.passmate.coin.domain.PaymentMethod
import kr.passmate.room.dto.EntryPaymentResponse
import java.time.LocalDateTime

/** 코인 충전 요청 (FR-050). */
@Schema(description = "코인 충전 요청")
data class CoinChargeRequest(
    @field:NotNull(message = "충전 금액은 필수입니다.")
    @field:Positive(message = "충전 금액은 0보다 커야 합니다.")
    @field:Schema(description = "충전할 코인. 1 C = ₩1")
    val amount: Int?,
    @field:Schema(description = "결제 수단. 비우면 기본 결제 수단을 쓴다")
    val method: PaymentMethod? = null,
    @field:Schema(description = "충전 직후 참가비를 차감할 방(선택). 있으면 확정 시 원스텝 처리")
    val roomId: Long? = null,
)

/**
 * 결제창 호출 파라미터 (FR-050).
 *
 * **여기 실리는 값은 브라우저까지 간다.** `storeId` · `channelKey` 는 그래도 되는 값이지만
 * API Secret · 웹훅 시크릿은 절대 이 DTO 에 들어가지 않는다.
 */
@Schema(description = "결제창 호출 파라미터")
data class CoinChargeResponse(
    val chargeId: Long,
    @field:Schema(description = "포트원에 넘길 주문 ID(V2 paymentId)")
    val paymentId: String,
    val storeId: String,
    val channelKey: String,
    val amount: Int,
    @field:Schema(description = "결제창에 표시될 주문명")
    val orderName: String,
    val status: CoinChargeStatus,
) {
    companion object {
        fun of(charge: CoinCharge, storeId: String, channelKey: String, orderName: String) = CoinChargeResponse(
            chargeId = charge.id,
            paymentId = charge.merchantUid,
            storeId = storeId,
            channelKey = channelKey,
            amount = charge.amount,
            orderName = orderName,
            status = charge.status,
        )
    }
}

/** 충전 승인 검증 결과 (FR-051). roomId 를 함께 보냈으면 참가비 차감까지 마친 상태다. */
@Schema(description = "충전 승인 결과")
data class CoinChargeConfirmResponse(
    val chargeId: Long,
    val status: CoinChargeStatus,
    @field:Schema(description = "충전된 코인")
    val amount: Int,
    @field:Schema(description = "충전(및 참가비 차감) 후 잔액")
    val balanceAfter: Int,
    val paidAt: LocalDateTime?,
    @field:Schema(description = "roomId 를 함께 보냈을 때만. 이어서 처리한 참가비 결제")
    val entryPayment: EntryPaymentResponse? = null,
)
