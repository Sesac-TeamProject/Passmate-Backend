package kr.passmate.coin.domain

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 코인 충전 한 건의 상태 전이 (FR-051).
 *
 * 확정(markPaid)은 **두 곳에서 들어온다** — 클라이언트 승인 호출과 포트원 웹훅.
 * 둘 다 와도 코인이 두 번 들어가면 안 되므로, 처음 확정한 쪽만 true 를 받는다.
 */
class CoinChargeTest {

    private val now = LocalDateTime.of(2026, 9, 3, 10, 0)

    @Test
    fun `충전 건은 READY 로 시작한다`() {
        val charge = charge()

        assertThat(charge.status).isEqualTo(CoinChargeStatus.READY)
        assertThat(charge.pgPaymentId).isNull()
        assertThat(charge.paidAt).isNull()
    }

    @Test
    fun `확정하면 PAID 가 되고 포트원 결제 id 가 남는다`() {
        val charge = charge()

        val first = charge.markPaid("payment-abc", now)

        assertThat(first).isTrue()
        assertThat(charge.status).isEqualTo(CoinChargeStatus.PAID)
        assertThat(charge.pgPaymentId).isEqualTo("payment-abc")
        assertThat(charge.paidAt).isEqualTo(now)
    }

    @Test
    fun `이미 확정된 건을 다시 확정하면 false 를 준다`() {
        val charge = charge()
        charge.markPaid("payment-abc", now)

        val second = charge.markPaid("payment-abc", now.plusMinutes(1))

        assertThat(second).isFalse()
    }

    @Test
    fun `두 번째 확정은 첫 확정 시각을 덮어쓰지 않는다`() {
        val charge = charge()
        charge.markPaid("payment-abc", now)

        charge.markPaid("payment-abc", now.plusMinutes(5))

        assertThat(charge.paidAt).isEqualTo(now)
    }

    @Test
    fun `실패로 표시하면 FAILED 가 되고 사유가 남는다`() {
        val charge = charge()

        charge.markFailed("카드 한도 초과", now)

        assertThat(charge.status).isEqualTo(CoinChargeStatus.FAILED)
        assertThat(charge.cancelReason).isEqualTo("카드 한도 초과")
    }

    @Test
    fun `이미 확정된 건은 실패로 되돌릴 수 없다`() {
        val charge = charge()
        charge.markPaid("payment-abc", now)

        assertThatThrownBy { charge.markFailed("뒤늦은 실패 통보", now) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.CONFLICT)
    }

    @Test
    fun `취소하면 CANCELED 가 되고 사유와 시각이 남는다`() {
        val charge = charge()
        charge.markPaid("payment-abc", now)

        charge.markCanceled("사용자 요청", now.plusHours(1))

        assertThat(charge.status).isEqualTo(CoinChargeStatus.CANCELED)
        assertThat(charge.canceledAt).isEqualTo(now.plusHours(1))
        assertThat(charge.cancelReason).isEqualTo("사용자 요청")
    }

    @Test
    fun `사유가 길면 컬럼 길이에 맞춰 자른다`() {
        val charge = charge()

        charge.markFailed("가".repeat(300), now)

        assertThat(charge.cancelReason).hasSize(CoinCharge.REASON_MAX)
    }

    @Test
    fun `남의 충전 건은 건드릴 수 없다`() {
        val charge = charge(userId = 1L)

        assertThatThrownBy { charge.verifyOwner(2L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.ACCESS_DENIED)
    }

    @Test
    fun `충전 금액이 0 이하면 만들 수 없다`() {
        assertThatThrownBy { charge(amount = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun charge(userId: Long = 1L, amount: Int = 10_000) = CoinCharge.of(
        userId = userId,
        roomId = null,
        amount = amount,
        method = PaymentMethod.CARD,
        merchantUid = "pm-charge-0001",
    )
}
