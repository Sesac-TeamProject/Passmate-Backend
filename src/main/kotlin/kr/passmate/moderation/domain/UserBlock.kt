package kr.passmate.moderation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity

/**
 * 호스트 차단 (FR-067). (차단한 사람, 차단당한 사람)당 한 줄(uk_user_block)이다.
 *
 * 차단은 **목록 노출과 프로필 접근만** 막는다 — PIN 을 직접 받아 들어가는 길은 열어 둔다.
 * 같은 수업을 듣는 사이에서 실수로 차단해도 수업 자체를 못 듣게 되면 안 된다.
 */
@Entity
@Table(name = "user_block")
class UserBlock(
    @Column(name = "blocker_user_id", nullable = false, updatable = false)
    val blockerUserId: Long,

    @Column(name = "blocked_user_id", nullable = false, updatable = false)
    val blockedUserId: Long,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set
}
