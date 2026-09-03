package kr.passmate.hostlevel.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.time.LocalDateTime

/**
 * 회원이 가진 뱃지. 회원·뱃지당 한 줄(uk_user_badge)이고, 아직 못 딴 뱃지도
 * 진행도를 담아 미리 만들어 둔다 — 화면이 "12/30" 을 보여줘야 한다.
 *
 * **한 번 딴 뱃지는 회수하지 않는다.** 평균 별점이 떨어졌다고 지난 성취를 빼앗으면
 * 뱃지가 성취 기록이 아니라 현재 상태 표시가 된다.
 */
@Entity
@Table(name = "user_badge")
class UserBadge(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "badge_id", nullable = false, updatable = false)
    val badgeId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "progress", nullable = false)
    var progress: Int = 0
        protected set

    @Column(name = "achieved_at")
    var achievedAt: LocalDateTime? = null
        protected set

    val achieved: Boolean get() = achievedAt != null

    /**
     * 진행도를 갱신하고, 문턱을 넘겼으면 그때 한 번만 획득으로 찍는다.
     * 이미 획득한 뱃지는 진행도가 내려가도 획득이 풀리지 않는다.
     */
    fun update(progress: Int, met: Boolean, at: LocalDateTime = LocalDateTime.now()) {
        this.progress = progress
        if (met && achievedAt == null) achievedAt = at
    }
}
