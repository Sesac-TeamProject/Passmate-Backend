package kr.passmate.hostlevel.repository

import kr.passmate.hostlevel.domain.Badge
import kr.passmate.hostlevel.domain.UserBadge
import org.springframework.data.jpa.repository.JpaRepository

interface BadgeRepository : JpaRepository<Badge, Long>

interface UserBadgeRepository : JpaRepository<UserBadge, Long> {

    fun findAllByUserId(userId: Long): List<UserBadge>

    /** 프로필 시트는 획득한 것만 보여준다. */
    fun findAllByUserIdAndAchievedAtIsNotNull(userId: Long): List<UserBadge>
}
