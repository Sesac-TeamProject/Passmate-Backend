package kr.passmate.moderation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.time.LocalDateTime

/**
 * 신고 (FR-067 · FR-071).
 *
 * 신고자는 회원이거나 게스트다 — 게스트는 계정이 없어 [reporterParticipantId] 로만 남는다.
 * 둘 다 채우지 않는 익명 접수는 없다. 누가 냈는지 모르면 중복·악의 신고를 가려낼 수 없다.
 *
 * 접수 이후(검토·처리·제재 연계)는 관리자 콘솔 몫이라 여기서는 열지 않는다.
 */
@Entity
@Table(name = "report")
class Report(
    @Column(name = "reporter_user_id", updatable = false)
    val reporterUserId: Long? = null,

    @Column(name = "reporter_participant_id", updatable = false)
    val reporterParticipantId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    val targetType: ReportTargetType,

    @Column(name = "target_id", nullable = false, updatable = false)
    val targetId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    val type: ReportType,

    @Column(name = "reason", nullable = false, length = REASON_MAX)
    val reason: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ReportStatus = ReportStatus.OPEN
        protected set

    @Column(name = "handled_by_user_id")
    var handledByUserId: Long? = null
        protected set

    @Column(name = "handled_at")
    var handledAt: LocalDateTime? = null
        protected set

    @Column(name = "resolution_memo", length = 500)
    var resolutionMemo: String? = null
        protected set

    @Column(name = "sanction_id")
    var sanctionId: Long? = null
        protected set

    companion object {
        const val REASON_MAX = 500
    }
}
