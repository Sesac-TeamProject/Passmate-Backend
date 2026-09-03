package kr.passmate.room.domain

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 참가비 결제 한 건의 상태 전이 (FR-050 · FR-052).
 *
 * 환급은 **코인으로** 돌려주는 것이라 원장(coin_transaction)과 짝을 이룬다.
 * 여기서는 이 행 자체의 규칙만 본다 — 코인이 실제로 움직이는지는 서비스 테스트가 본다.
 */
class EntryPaymentTest {

    private val paidAt = LocalDateTime.of(2026, 9, 3, 10, 0)

    @Test
    fun `결제하면 PAID 로 시작하고 환급 흔적이 없다`() {
        val payment = payment()

        assertThat(payment.status).isEqualTo(EntryPaymentStatus.PAID)
        assertThat(payment.paidAt).isEqualTo(paidAt)
        assertThat(payment.refundedAt).isNull()
        assertThat(payment.refundReason).isNull()
    }

    @Test
    fun `환급하면 REFUNDED 로 바뀌고 사유와 처리자가 남는다`() {
        val payment = payment()
        val at = paidAt.plusMinutes(5)

        payment.refund("학생 취소", refundedByUserId = 7L, at = at)

        assertThat(payment.status).isEqualTo(EntryPaymentStatus.REFUNDED)
        assertThat(payment.refundedAt).isEqualTo(at)
        assertThat(payment.refundReason).isEqualTo("학생 취소")
        assertThat(payment.refundedByUserId).isEqualTo(7L)
    }

    @Test
    fun `이미 환급한 건은 다시 환급하지 못한다`() {
        val payment = payment()
        payment.refund("학생 취소", refundedByUserId = 7L, at = paidAt.plusMinutes(5))

        assertThatThrownBy { payment.refund("또 취소", refundedByUserId = 7L, at = paidAt.plusMinutes(9)) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.ALREADY_REFUNDED)
    }

    @Test
    fun `환급 사유가 너무 길면 컬럼 길이에 맞춰 자른다`() {
        val payment = payment()

        payment.refund("가".repeat(300), refundedByUserId = 7L, at = paidAt)

        assertThat(payment.refundReason).hasSize(EntryPayment.REASON_MAX)
    }

    @Test
    fun `입장하면 참가자가 연결된다`() {
        val payment = payment()

        payment.linkParticipant(42L)

        assertThat(payment.participantId).isEqualTo(42L)
    }

    @Test
    fun `남의 결제는 건드릴 수 없다`() {
        val payment = payment(userId = 1L)

        assertThatThrownBy { payment.verifyOwner(2L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.ACCESS_DENIED)
    }

    @Test
    fun `참가비가 0 이하면 결제를 만들 수 없다`() {
        assertThatThrownBy { payment(amount = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun payment(userId: Long = 1L, amount: Int = 1000) = EntryPayment.of(
        paymentNo = "PM-2026-0903-0001",
        roomId = 10L,
        userId = userId,
        amount = amount,
        paidAt = paidAt,
    )
}
