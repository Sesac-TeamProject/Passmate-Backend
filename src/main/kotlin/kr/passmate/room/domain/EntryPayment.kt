package kr.passmate.room.domain

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

/** 참가비 결제 상태. `entry_payment.status` 와 값이 같다. */
enum class EntryPaymentStatus {
    /** 코인이 차감됐고 입장 자격이 있다 */
    PAID,

    /** 코인을 100% 돌려줬다. 입장 자격도 사라진다 */
    REFUNDED,
}

/**
 * 유료 방 참가비 결제 한 건 (FR-050 · FR-052). 방 입장권이라 room 이 소유한다 —
 * 코인은 그 값을 치르는 수단일 뿐이고, 입장 게이트를 room 안에서 닫으려면
 * 이 표가 room 쪽에 있어야 room → coin 한 방향으로 정리된다.
 *
 * 현금이 아니라 **코인으로** 돌려준다(FR-052). 실제 코인 이동은 CoinService 가 하고
 * 이 엔티티는 "돌려줬다"는 사실만 기록한다.
 */
@Entity
@Table(name = "entry_payment")
class EntryPayment private constructor(

    /** 영수증 번호 PM-YYYY-MMDD-NNNN. 사용자에게 보이는 유일한 식별자다 */
    @Column(name = "payment_no", nullable = false, updatable = false, length = 24)
    val paymentNo: String,

    @Column(name = "room_id", nullable = false, updatable = false)
    val roomId: Long,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Int,

    @Column(name = "paid_at", nullable = false, updatable = false)
    val paidAt: LocalDateTime,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: EntryPaymentStatus = EntryPaymentStatus.PAID
        protected set

    /** 입장을 마치면 연결된다. 결제만 하고 아직 안 들어왔으면 null */
    @Column(name = "participant_id")
    var participantId: Long? = null
        protected set

    @Column(name = "refunded_at")
    var refundedAt: LocalDateTime? = null
        protected set

    @Column(name = "refund_reason", length = REASON_MAX)
    var refundReason: String? = null
        protected set

    /** 환급을 실행한 주체. 본인 취소면 본인, 관리자 환불이면 관리자 */
    @Column(name = "refunded_by_user_id")
    var refundedByUserId: Long? = null
        protected set

    /** 아직 살아 있는 결제인지. 입장 자격이 이걸로 결정된다 */
    val active: Boolean
        get() = status == EntryPaymentStatus.PAID

    /**
     * 환급 처리. 이미 환급된 건은 막는다 — 두 번 통과하면 코인을 두 배로 돌려준다.
     * 방 취소·강퇴처럼 남이 대신 처리하는 경우가 있어 처리자를 따로 받는다.
     */
    fun refund(reason: String, refundedByUserId: Long, at: LocalDateTime = LocalDateTime.now()) {
        if (status == EntryPaymentStatus.REFUNDED) {
            throw BusinessException(ErrorCode.ALREADY_REFUNDED)
        }
        status = EntryPaymentStatus.REFUNDED
        refundedAt = at
        refundReason = reason.take(REASON_MAX)
        this.refundedByUserId = refundedByUserId
    }

    /** 입장이 끝나면 참가자를 연결한다(ERD entry_payment.participant_id). */
    fun linkParticipant(participantId: Long) {
        this.participantId = participantId
    }

    /** 본인 결제인지 확인하고, 아니면 403 으로 막는다. */
    fun verifyOwner(userId: Long) {
        if (this.userId != userId) throw BusinessException(ErrorCode.ACCESS_DENIED)
    }

    companion object {
        const val REASON_MAX = 200

        fun of(
            paymentNo: String,
            roomId: Long,
            userId: Long,
            amount: Int,
            paidAt: LocalDateTime = LocalDateTime.now(),
        ): EntryPayment {
            require(amount > 0) { "참가비는 0보다 커야 합니다." }
            return EntryPayment(
                paymentNo = paymentNo,
                roomId = roomId,
                userId = userId,
                amount = amount,
                paidAt = paidAt,
            )
        }
    }
}
