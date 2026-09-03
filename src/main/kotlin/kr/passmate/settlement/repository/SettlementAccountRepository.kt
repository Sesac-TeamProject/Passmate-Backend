package kr.passmate.settlement.repository

import kr.passmate.settlement.domain.SettlementAccount
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementAccountRepository : JpaRepository<SettlementAccount, Long> {

    fun findByUserId(userId: Long): SettlementAccount?
}
