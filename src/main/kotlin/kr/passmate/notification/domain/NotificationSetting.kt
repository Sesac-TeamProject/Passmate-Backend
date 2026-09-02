package kr.passmate.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity

/**
 * 알림 항목별 on/off (FR-065, C-02 v3 · M-12).
 *
 * 회원당 한 줄(uk_notification_setting_user)이고 **기본은 전부 켜짐**이다 —
 * 세션 시작·평가 요청은 놓치면 그 자리에서 쓸모가 사라지는 알림이라 opt-out 으로 둔다.
 */
@Entity
@Table(name = "notification_setting")
class NotificationSetting(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    /** 참여 예정인 방의 세션이 시작됐다 */
    @Column(name = "session_start", nullable = false)
    var sessionStart: Boolean = true
        protected set

    /** 답안을 낸 세션이 끝나 평가를 남길 수 있다 */
    @Column(name = "rating_request", nullable = false)
    var ratingRequest: Boolean = true
        protected set

    /** 월 정산이 지급됐다 */
    @Column(name = "settlement_done", nullable = false)
    var settlementDone: Boolean = true
        protected set

    fun update(sessionStart: Boolean, ratingRequest: Boolean, settlementDone: Boolean) {
        this.sessionStart = sessionStart
        this.ratingRequest = ratingRequest
        this.settlementDone = settlementDone
    }
}
