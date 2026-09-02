package kr.passmate.hostlevel.repository

import kr.passmate.hostlevel.domain.HostProfile
import org.springframework.data.jpa.repository.JpaRepository

interface HostProfileRepository : JpaRepository<HostProfile, Long> {

    fun findByUserId(userId: Long): HostProfile?

    fun findAllByUserIdIn(userIds: Collection<Long>): List<HostProfile>
}
