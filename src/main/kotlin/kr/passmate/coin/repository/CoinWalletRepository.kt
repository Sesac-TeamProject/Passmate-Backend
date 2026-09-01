package kr.passmate.coin.repository

import jakarta.persistence.LockModeType
import kr.passmate.coin.domain.CoinWallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoinWalletRepository : JpaRepository<CoinWallet, Long> {

    fun findByUserId(userId: Long): CoinWallet?

    fun existsByUserId(userId: Long): Boolean

    /**
     * 잔액을 바꾸기 전에 거는 비관적 락(`SELECT … FOR UPDATE`).
     * Redis 를 쓰지 않으므로 코인 동시성은 전적으로 이 락이 막는다 —
     * 잔액을 **읽고 쓰는** 경로는 예외 없이 이쪽으로 들어와야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from CoinWallet w where w.userId = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): CoinWallet?
}
