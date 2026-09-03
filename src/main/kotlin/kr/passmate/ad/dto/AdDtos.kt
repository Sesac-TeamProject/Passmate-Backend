package kr.passmate.ad.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import kr.passmate.ad.domain.AdCampaign
import kr.passmate.ad.domain.AdEventType
import kr.passmate.ad.domain.AdPlacement

/**
 * 화면에 걸 광고 소재 (FR-073).
 *
 * 계약액·집계 수치는 담지 않는다 — 누구나 보는 응답에 광고주와의 계약 금액이 실릴 이유가 없다.
 */
@Schema(description = "노출할 광고 소재")
data class AdResponse(
    @field:Schema(description = "노출·클릭을 집계할 때 이 값을 보낸다")
    val adId: Long,
    val advertiser: String,
    val creativeUrl: String,
    val linkUrl: String,
    val placement: AdPlacement,
)

@Schema(description = "그 자리에 걸 광고 목록")
data class AdListResponse(
    val placement: AdPlacement,
    @field:Schema(description = "집행 중인 광고가 없으면 비어 있다. 화면은 자리를 접는다")
    val ads: List<AdResponse>,
) {
    companion object {
        fun of(placement: AdPlacement, campaigns: List<AdCampaign>) = AdListResponse(
            placement = placement,
            ads = campaigns.map {
                AdResponse(
                    adId = it.id,
                    advertiser = it.advertiser,
                    creativeUrl = it.creativeUrl,
                    linkUrl = it.linkUrl,
                    placement = it.placement,
                )
            },
        )
    }
}

@Schema(description = "광고 노출·클릭 집계")
data class AdEventRequest(
    @field:Schema(description = "IMPRESSION 또는 CLICK")
    @field:NotNull
    val type: AdEventType,
)
