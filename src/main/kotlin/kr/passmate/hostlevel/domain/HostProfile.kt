package kr.passmate.hostlevel.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 호스트 명성. 회원당 한 장(uk_host_profile_user)이다.
 *
 * 여기 담긴 집계는 전부 다른 표에서 다시 셀 수 있는 값이지만 굳혀 둔다 —
 * 등급은 방 목록·프로필 시트·유료 방 개설 게이트에서 계속 읽히는데,
 * 읽을 때마다 방·참가자·평가를 훑으면 감당이 안 된다.
 */
@Entity
@Table(name = "host_profile")
class HostProfile(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    /** TINYINT 컬럼이라 JDBC 타입을 맞춘다 — 안 맞추면 ddl-auto: validate 가 기동을 막는다 */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TINYINT)
    @Column(name = "level", nullable = false)
    var level: Int = 1
        protected set

    @Column(name = "level_achieved_at")
    var levelAchievedAt: LocalDateTime? = null
        protected set

    @Column(name = "rooms_hosted", nullable = false)
    var roomsHosted: Int = 0
        protected set

    @Column(name = "sessions_completed", nullable = false)
    var sessionsCompleted: Int = 0
        protected set

    @Column(name = "total_students", nullable = false)
    var totalStudents: Int = 0
        protected set

    @Column(name = "avg_rating", precision = 3, scale = 2)
    var avgRating: BigDecimal? = null
        protected set

    @Column(name = "rating_count", nullable = false)
    var ratingCount: Int = 0
        protected set

    /** 유지 판정 기간 안의 활동 횟수. 컬럼 이름이 30d 지만 기간은 정책값을 따른다 */
    @Column(name = "active_last_30d", nullable = false)
    var activeLast30d: Int = 0
        protected set

    @Column(name = "last_evaluated_at")
    var lastEvaluatedAt: LocalDateTime? = null
        protected set

    @Column(name = "next_evaluation_at")
    var nextEvaluationAt: LocalDateTime? = null
        protected set

    /** 판정 때마다 집계를 새로 박는다. 등급은 이 값들로 따로 정한다. */
    fun refreshMetrics(
        roomsHosted: Int,
        totalStudents: Int,
        avgRating: BigDecimal?,
        ratingCount: Int,
        activeInWindow: Int,
    ) {
        this.roomsHosted = roomsHosted
        // 방 운영 1회 = 시작해서 종료까지 간 세션 1회라 두 값은 같은 것을 센다
        this.sessionsCompleted = roomsHosted
        this.totalStudents = totalStudents
        this.avgRating = avgRating
        this.ratingCount = ratingCount
        this.activeLast30d = activeInWindow
    }

    /** 등급이 실제로 바뀔 때만 달성일을 새로 찍는다 — 판정만 돌았다고 날짜가 밀리면 안 된다. */
    fun applyLevel(level: Int, at: LocalDateTime = LocalDateTime.now()) {
        if (this.level == level) return
        this.level = level
        this.levelAchievedAt = at
    }

    fun markEvaluated(at: LocalDateTime, nextAt: LocalDateTime?) {
        this.lastEvaluatedAt = at
        this.nextEvaluationAt = nextAt
    }
}
