package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.room.domain.EntryPayment
import kr.passmate.room.domain.EntryPaymentStatus
import java.time.LocalDateTime

/** 참가비 결제 결과 (FR-050). 영수증 번호와 남은 코인을 함께 준다. */
@Schema(description = "참가비 결제")
data class EntryPaymentResponse(
    val paymentId: Long,
    @field:Schema(description = "영수증 번호 PM-YYYY-MMDD-NNNN")
    val paymentNo: String,
    val roomId: Long,
    @field:Schema(description = "차감한 코인")
    val amount: Int,
    val status: EntryPaymentStatus,
    @field:Schema(description = "차감 후 남은 코인")
    val balanceAfter: Int,
    val paidAt: LocalDateTime,
) {
    companion object {
        fun of(payment: EntryPayment, balanceAfter: Int) = EntryPaymentResponse(
            paymentId = payment.id,
            paymentNo = payment.paymentNo,
            roomId = payment.roomId,
            amount = payment.amount,
            status = payment.status,
            balanceAfter = balanceAfter,
            paidAt = payment.paidAt,
        )
    }
}

/** 참가비 취소 결과 (FR-052). 코인으로 돌려준 것이지 현금 환불이 아니다. */
@Schema(description = "참가비 취소")
data class EntryPaymentCancelResponse(
    val paymentId: Long,
    val paymentNo: String,
    val roomId: Long,
    val status: EntryPaymentStatus,
    @field:Schema(description = "돌려준 코인. 항상 결제 전액이다")
    val refundedAmount: Int,
    @field:Schema(description = "환급 후 코인 잔액")
    val balanceAfter: Int,
    val refundedAt: LocalDateTime?,
) {
    companion object {
        fun of(payment: EntryPayment, balanceAfter: Int) = EntryPaymentCancelResponse(
            paymentId = payment.id,
            paymentNo = payment.paymentNo,
            roomId = payment.roomId,
            status = payment.status,
            refundedAmount = payment.amount,
            balanceAfter = balanceAfter,
            refundedAt = payment.refundedAt,
        )
    }
}
