package kr.passmate.settlement.repository

import kr.passmate.settlement.domain.HostEarning
import org.springframework.data.jpa.repository.JpaRepository

interface HostEarningRepository : JpaRepository<HostEarning, Long> {

    /** 내 적립 내역. 최근 것부터 — 정산 화면이 먼저 보여줄 것은 방금 끝낸 세션이다. */
    fun findAllByHostUserIdOrderByEarnedAtDesc(hostUserId: Long): List<HostEarning>
}
