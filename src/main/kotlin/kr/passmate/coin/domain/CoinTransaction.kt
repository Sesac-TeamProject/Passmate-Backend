package kr.passmate.coin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity

/**
 * 코인 원장. **append-only** — 한 번 남긴 줄은 고치지도 지우지도 않는다.
 * 잘못 나간 차감은 반대 부호의 REFUND 를 한 줄 더 쌓아서 되돌린다.
 *
 * `coin_wallet.balance` 는 이 표의 합계를 캐시한 값이라,
 * 원장과 잔액은 반드시 **같은 트랜잭션에서 함께** 움직여야 한다(CoinService 가 그 자리다).
 */
@Entity
@Table(name = "coin_transaction")
class CoinTransaction(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    val type: CoinTransactionType,

    /** 부호 있음: + 충전·환급, - 차감 */
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Int,

    /** 이 줄을 적용한 뒤의 잔액. 원장만 훑어도 잔액 흐름을 되짚을 수 있게 박아둔다 */
    @Column(name = "balance_after", nullable = false, updatable = false)
    val balanceAfter: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", updatable = false, length = 20)
    val refType: CoinRefType? = null,

    @Column(name = "ref_id", updatable = false)
    val refId: Long? = null,

    @Column(name = "memo", updatable = false, length = MEMO_MAX)
    val memo: String? = null,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    companion object {
        const val MEMO_MAX = 200
    }
}
