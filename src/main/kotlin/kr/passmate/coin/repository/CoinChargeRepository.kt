package kr.passmate.coin.repository

import jakarta.persistence.LockModeType
import kr.passmate.coin.domain.CoinCharge
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoinChargeRepository : JpaRepository<CoinCharge, Long> {

    fun findByMerchantUid(merchantUid: String): CoinCharge?

    fun existsByMerchantUid(merchantUid: String): Boolean

    /**
     * 확정 처리용 비관적 락. 승인 호출과 웹훅이 **동시에** 들어와도 한쪽만 상태를 바꾸게 한다 —
     * 둘 다 READY 를 읽으면 markPaid 가 양쪽 다 true 를 돌려주고 코인이 두 번 들어간다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CoinCharge c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): CoinCharge?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CoinCharge c where c.merchantUid = :merchantUid")
    fun findByMerchantUidForUpdate(@Param("merchantUid") merchantUid: String): CoinCharge?
}
