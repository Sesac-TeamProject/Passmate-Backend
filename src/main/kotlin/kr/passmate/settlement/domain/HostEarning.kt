package kr.passmate.settlement.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** 적립 상태 (FR-057). */
enum class HostEarningStatus {
    /** 적립됐고 아직 지급 전 */
    PENDING,

    /** 정산 회차에 실려 지급 완료 */
    SETTLED,

    /** 계좌 미등록 등으로 보류 */
    HELD,

    /** 최소 정산액 미만이라 다음 회차로 이월 */
    CARRIED,
}

/**
 * 유료 세션 한 건의 호스트 수익 (FR-055). 방당 한 줄(uk_host_earning_room)이다.
 *
 * 배분은 **적립 시점에 계산해 박아 둔다.** 나중에 배분율이 바뀌어도 이미 끝난 세션의
 * 정산 금액은 그때 약속한 값이어야 한다.
 */
@Entity
@Table(name = "host_earning")
class HostEarning private constructor(
    @Column(name = "room_id", nullable = false, updatable = false)
    val roomId: Long,

    @Column(name = "host_user_id", nullable = false, updatable = false)
    val hostUserId: Long,

    @Column(name = "participant_count", nullable = false)
    val participantCount: Int,

    @Column(name = "gross", nullable = false)
    val gross: Int,

    @Column(name = "platform_fee", nullable = false)
    val platformFee: Int,

    @Column(name = "net", nullable = false)
    val net: Int,

    @Column(name = "earned_at", nullable = false, updatable = false)
    val earnedAt: LocalDateTime,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: HostEarningStatus = HostEarningStatus.PENDING
        protected set

    @Column(name = "settlement_id")
    var settlementId: Long? = null
        protected set

    /** 아직 지급되지 않은 적립인지. 지급 예정액이 이걸로 모인다. */
    val payable: Boolean
        get() = status == HostEarningStatus.PENDING || status == HostEarningStatus.CARRIED

    fun markSettled(settlementId: Long) {
        this.settlementId = settlementId
        this.status = HostEarningStatus.SETTLED
    }

    fun hold() {
        status = HostEarningStatus.HELD
    }

    fun carry() {
        status = HostEarningStatus.CARRIED
    }

    companion object {
        /**
         * 참가비 총액에서 호스트 몫과 플랫폼 수수료를 갈라 적립한다.
         *
         * 수수료를 먼저 반올림하고 나머지를 호스트에게 준다 —
         * 양쪽을 따로 반올림하면 합이 총액과 1원 어긋날 수 있다.
         */
        fun of(
            roomId: Long,
            hostUserId: Long,
            participantCount: Int,
            gross: Int,
            hostRate: Double,
            earnedAt: LocalDateTime = LocalDateTime.now(),
        ): HostEarning {
            val fee = (gross * (1.0 - hostRate)).roundToInt()
            return HostEarning(
                roomId = roomId,
                hostUserId = hostUserId,
                participantCount = participantCount,
                gross = gross,
                platformFee = fee,
                net = gross - fee,
                earnedAt = earnedAt,
            )
        }
    }
}
