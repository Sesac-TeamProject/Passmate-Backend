package kr.passmate.settlement.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.time.LocalDateTime

/**
 * 정산 계좌 (FR-056). 회원당 하나(uk_settlement_account_user)다.
 *
 * 계좌번호는 **암호문으로만** 들고 있다([accountNoEnc]). 정산하려면 원문을 복구해야 해서
 * 해시가 아니라 암호화이고, 그래서 이 엔티티는 원문을 노출하는 접근자를 두지 않는다 —
 * 복호화는 필요한 서비스가 명시적으로 한다.
 */
@Entity
@Table(name = "settlement_account")
class SettlementAccount(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "bank_code", nullable = false, length = 10)
    var bankCode: String,

    @Column(name = "bank_name", nullable = false, length = 50)
    var bankName: String,

    @Column(name = "account_no_enc", nullable = false, length = 255)
    var accountNoEnc: String,

    @Column(name = "holder_name", nullable = false, length = 50)
    var holderName: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    /** 예금주 실명 확인을 마친 시각. 지금은 채우는 경로가 없다 */
    @Column(name = "verified_at")
    var verifiedAt: LocalDateTime? = null
        protected set

    val verified: Boolean get() = verifiedAt != null

    /**
     * 계좌를 바꾼다. **검증 상태는 함께 지운다** —
     * 계좌가 바뀌었는데 예전 계좌로 받은 확인이 그대로 남으면 검증의 뜻이 없다.
     */
    fun update(bankCode: String, bankName: String, accountNoEnc: String, holderName: String) {
        this.bankCode = bankCode
        this.bankName = bankName
        this.accountNoEnc = accountNoEnc
        this.holderName = holderName
        this.verifiedAt = null
    }
}
