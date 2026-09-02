package kr.passmate.moderation.repository

import kr.passmate.moderation.domain.UserBlock
import org.springframework.data.jpa.repository.JpaRepository

interface UserBlockRepository : JpaRepository<UserBlock, Long> {

    fun findAllByBlockerUserIdOrderByIdDesc(blockerUserId: Long): List<UserBlock>

    fun existsByBlockerUserIdAndBlockedUserId(blockerUserId: Long, blockedUserId: Long): Boolean

    fun deleteByBlockerUserIdAndBlockedUserId(blockerUserId: Long, blockedUserId: Long)

    /** 내가 차단한 사람 id 만. 공개 목록에서 걸러낼 때 쓴다. */
    @org.springframework.data.jpa.repository.Query(
        "select b.blockedUserId from UserBlock b where b.blockerUserId = :blockerUserId",
    )
    fun findBlockedUserIds(
        @org.springframework.data.repository.query.Param("blockerUserId") blockerUserId: Long,
    ): List<Long>
}
