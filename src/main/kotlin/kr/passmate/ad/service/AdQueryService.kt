package kr.passmate.ad.service

import kr.passmate.ad.domain.AdCampaignStatus
import kr.passmate.ad.domain.AdPlacement
import kr.passmate.ad.dto.AdListResponse
import kr.passmate.ad.repository.AdCampaignRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 노출 위치에 걸 광고 조회 (FR-073).
 *
 * 결과 화면·대기실 배너에 뜨는 값이라 **로그인 없이도 열린다.**
 */
@Service
@Transactional(readOnly = true)
class AdQueryService(
    private val adCampaignRepository: AdCampaignRepository,
) {

    fun adsAt(placement: AdPlacement, today: LocalDate = LocalDate.now()): AdListResponse {
        // 승인된 것만, 그리고 오늘이 집행 기간 안에 든 것만
        val campaigns = adCampaignRepository
            .findAllByPlacementAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByIdDesc(
                placement, AdCampaignStatus.ACTIVE, today, today,
            )
        return AdListResponse.of(placement, campaigns)
    }
}
