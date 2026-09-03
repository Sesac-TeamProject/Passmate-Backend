package kr.passmate.coin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import java.time.LocalDateTime

/** 충전 건의 상태. `coin_charge.status` 와 값이 같다. */
enum class CoinChargeStatus {
    /** 결제창을 띄울 준비만 된 상태. 아직 돈이 오가지 않았다 */
    READY,

    /** 결제가 확정돼 코인이 들어갔다 */
    PAID,

    /** 결제가 실패했다 */
    FAILED,

    /** 확정된 결제를 되돌렸다 */
    CANCELED,
}

/**
 * 코인 충전 한 건 (FR-051). 포트원 결제 1건과 1:1 이다.
 *
 * 확정([markPaid])이 **두 경로로 들어온다** — 클라이언트의 승인 호출과 포트원 웹훅.
 * 어느 쪽이 먼저 와도 되고 둘 다 와도 되지만, 코인은 한 번만 들어가야 한다.
 * 그래서 상태 전이가 곧 멱등 장치다: 처음 확정한 호출만 `true` 를 받고,
 * 코인을 넣는 쪽은 그 `true` 를 보고 움직인다.
 */
@Entity
@Table(name = "coin_charge")
class CoinCharge private constructor(

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    /** 충전 직후 참가비를 차감할 방(선택). "충전 → 차감하고 입장" 원스텝에 쓴다 */
    @Column(name = "room_id", updatable = false)
    val roomId: Long?,

    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 30)
    val method: PaymentMethod?,

    /** 우리가 발급한 주문 ID. 포트원 V2 의 `paymentId` 로 나간다 */
    @Column(name = "merchant_uid", nullable = false, updatable = false, length = 64)
    val merchantUid: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "pg_provider", nullable = false, length = 20)
    val pgProvider: String = PROVIDER_PORTONE

    /** 포트원이 발급한 결제 ID. 확정될 때 채워진다 */
    @Column(name = "pg_payment_id", length = 100)
    var pgPaymentId: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CoinChargeStatus = CoinChargeStatus.READY
        protected set

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null
        protected set

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null
        protected set

    @Column(name = "cancel_reason", length = REASON_MAX)
    var cancelReason: String? = null
        protected set

    /**
     * 결제를 확정한다.
     *
     * @return **이번 호출이 처음 확정한 것이면 true.** 이미 PAID 였으면 false 이고
     *   아무것도 바꾸지 않는다 — 확정 시각을 나중 호출로 덮으면 영수증이 흔들린다.
     *   코인을 넣는 쪽은 이 값이 true 일 때만 움직여야 한다.
     */
    fun markPaid(pgPaymentId: String, at: LocalDateTime = LocalDateTime.now()): Boolean {
        if (status == CoinChargeStatus.PAID) return false
        this.pgPaymentId = pgPaymentId
        this.status = CoinChargeStatus.PAID
        this.paidAt = at
        return true
    }

    /**
     * 결제 실패로 표시한다. 이미 확정된 건은 되돌리지 않는다 —
     * 뒤늦은 실패 통보로 PAID 를 뒤집으면 코인은 들어갔는데 기록만 실패로 남는다.
     */
    fun markFailed(reason: String, at: LocalDateTime = LocalDateTime.now()) {
        if (status == CoinChargeStatus.PAID) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 확정된 결제는 실패로 바꿀 수 없습니다.")
        }
        status = CoinChargeStatus.FAILED
        cancelReason = reason.take(REASON_MAX)
        canceledAt = at
    }

    /** 확정된 결제를 되돌린다(포트원 취소 웹훅). 코인 회수는 서비스가 따로 처리한다. */
    fun markCanceled(reason: String, at: LocalDateTime = LocalDateTime.now()) {
        status = CoinChargeStatus.CANCELED
        cancelReason = reason.take(REASON_MAX)
        canceledAt = at
    }

    fun verifyOwner(userId: Long) {
        if (this.userId != userId) throw BusinessException(ErrorCode.ACCESS_DENIED)
    }

    companion object {
        const val REASON_MAX = 200
        private const val PROVIDER_PORTONE = "PORTONE"

        fun of(
            userId: Long,
            roomId: Long?,
            amount: Int,
            method: PaymentMethod?,
            merchantUid: String,
        ): CoinCharge {
            require(amount > 0) { "충전 금액은 0보다 커야 합니다." }
            return CoinCharge(
                userId = userId,
                roomId = roomId,
                amount = amount,
                method = method,
                merchantUid = merchantUid,
            )
        }
    }
}
