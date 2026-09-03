package kr.passmate.ad.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity
import java.time.LocalDateTime

/**
 * 노출·클릭 한 건 (FR-073).
 *
 * 캠페인의 누적 수치는 [AdCampaign] 에도 있지만 행을 따로 남긴다 —
 * 나중에 "언제 몇 번" 을 물으면 누적 숫자로는 답할 수 없다.
 *
 * 누가 봤는지는 있으면 담고 없으면 비운다. 게스트는 참가자 id 로만 남는다.
 */
@Entity
@Table(name = "ad_event")
class AdEvent(
    @Column(name = "campaign_id", nullable = false, updatable = false)
    val campaignId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    val type: AdEventType,

    @Column(name = "user_id", updatable = false)
    val userId: Long? = null,

    @Column(name = "participant_id", updatable = false)
    val participantId: Long? = null,

    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: LocalDateTime = LocalDateTime.now(),
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set
}
