package kr.passmate.ad.repository

import kr.passmate.ad.domain.AdCampaign
import kr.passmate.ad.domain.AdCampaignStatus
import kr.passmate.ad.domain.AdEvent
import kr.passmate.ad.domain.AdPlacement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.time.LocalDate

interface AdCampaignRepository : JpaRepository<AdCampaign, Long> {

    /**
     * 그 자리에 지금 걸 수 있는 광고. 승인됐고 기간 안에 든 것만 (FR-073).
     * idx_ad_campaign_placement 가 그대로 받쳐 준다.
     */
    fun findAllByPlacementAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByIdDesc(
        placement: AdPlacement,
        status: AdCampaignStatus,
        startsAt: LocalDate,
        endsAt: LocalDate,
    ): List<AdCampaign>

    /**
     * 집계를 올릴 때만 쓰는 잠금 조회. 같은 광고에 노출이 동시에 몰리면
     * 잠그지 않은 증가는 사라진다(lost update).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AdCampaign c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): AdCampaign?
}

interface AdEventRepository : JpaRepository<AdEvent, Long>
