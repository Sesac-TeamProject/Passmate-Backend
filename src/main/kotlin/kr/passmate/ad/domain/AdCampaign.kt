package kr.passmate.ad.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.time.LocalDate

/**
 * 광고 캠페인 (FR-072).
 *
 * 등록·승인·종료는 관리자 콘솔 몫이라 지금은 조회와 집계만 연다.
 * [impressions]·[clicks] 는 ad_event 를 세면 나오는 값이지만, 목록 화면이 캠페인마다
 * 이벤트를 세지 않도록 굳혀 둔다.
 */
@Entity
@Table(name = "ad_campaign")
class AdCampaign(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "advertiser", nullable = false, length = 100)
    var advertiser: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "placement", nullable = false, length = 30)
    var placement: AdPlacement,

    @Column(name = "creative_url", nullable = false, length = 500)
    var creativeUrl: String,

    @Column(name = "link_url", nullable = false, length = 500)
    var linkUrl: String,

    @Column(name = "starts_at", nullable = false)
    var startsAt: LocalDate,

    @Column(name = "ends_at", nullable = false)
    var endsAt: LocalDate,

    @Column(name = "contract_amount")
    var contractAmount: Int? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: AdCampaignStatus = AdCampaignStatus.PENDING_REVIEW
        protected set

    @Column(name = "impressions", nullable = false)
    var impressions: Int = 0
        protected set

    @Column(name = "clicks", nullable = false)
    var clicks: Int = 0
        protected set

    /** 검수 승인 — 관리자 콘솔이 생기면 그쪽에서 부른다. */
    fun activate() {
        status = AdCampaignStatus.ACTIVE
    }

    fun end() {
        status = AdCampaignStatus.ENDED
    }

    /** 이벤트가 들어올 때마다 집계를 올린다. 이벤트 행은 따로 남는다(원장). */
    fun record(type: AdEventType) {
        when (type) {
            AdEventType.IMPRESSION -> impressions += 1
            AdEventType.CLICK -> clicks += 1
        }
    }
}
