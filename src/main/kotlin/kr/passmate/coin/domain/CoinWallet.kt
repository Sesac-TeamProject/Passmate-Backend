package kr.passmate.coin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import java.time.LocalDateTime

/**
 * 코인 지갑. 1 C = ₩1. 첫 로그인 시 생성한다(ERD coin_wallet).
 *
 * balance 는 원장(coin_transaction)의 캐시다. 잔액을 바꾸는 메서드는 반드시
 * 같은 트랜잭션에서 coin_transaction 을 함께 남기는 CoinService 안에서만 호출한다.
 * 동시성은 CoinWalletRepository 의 비관적 락으로 막는다.
 */
@Entity
@Table(name = "coin_wallet")
class CoinWallet(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "balance", nullable = false)
    var balance: Int = 0
        protected set

    @Column(name = "default_payment_method", length = 30)
    var defaultPaymentMethod: String? = null
        protected set

    @Column(name = "last_transaction_at")
    var lastTransactionAt: LocalDateTime? = null
        protected set

    /** 충전 — 결제 확정(coin_charge.markPaid) 뒤에만 부른다. */
    fun charge(amount: Int, at: LocalDateTime = LocalDateTime.now()) {
        require(amount > 0) { "충전 금액은 0보다 커야 합니다." }
        balance += amount
        lastTransactionAt = at
    }

    /**
     * 차감 — 잔액이 모자라면 402 로 막는다. DB 의 chk_coin_wallet_balance 와 이중으로 지킨다.
     *
     * 부족할 때는 **얼마나 모자란지**를 함께 던진다. 화면이 "8,800 C 충전 필요"를
     * 그려야 하는데, 잔액을 다시 조회하면 그 사이 값이 바뀔 수 있다.
     */
    fun deduct(amount: Int, at: LocalDateTime = LocalDateTime.now()) {
        require(amount > 0) { "차감 금액은 0보다 커야 합니다." }
        if (balance < amount) {
            throw BusinessException(ErrorCode.INSUFFICIENT_COINS, data = shortfallOf(amount, balance))
        }
        balance -= amount
        lastTransactionAt = at
    }

    fun changeDefaultPaymentMethod(method: String?) {
        defaultPaymentMethod = method
    }

    companion object {
        /** 402 응답에 실을 부족분. 지갑이 아예 없는 경우에도 같은 모양을 쓴다 */
        fun shortfallOf(required: Int, balance: Int): Map<String, Any> = mapOf(
            "required" to required,
            "balance" to balance,
            "shortfall" to (required - balance).coerceAtLeast(0),
        )
    }
}
