package kr.passmate.coin.repository

import kr.passmate.coin.domain.CoinWallet
import org.springframework.data.jpa.repository.JpaRepository

interface CoinWalletRepository : JpaRepository<CoinWallet, Long> {

    fun findByUserId(userId: Long): CoinWallet?

    fun existsByUserId(userId: Long): Boolean
}
