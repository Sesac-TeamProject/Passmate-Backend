package kr.passmate.ad.service

import kr.passmate.ad.domain.AdEvent
import kr.passmate.ad.domain.AdEventType
import kr.passmate.ad.repository.AdCampaignRepository
import kr.passmate.ad.repository.AdEventRepository
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 노출·클릭 집계 (FR-073).
 *
 * 로그인하지 않은 사람의 노출도 받는다 — 대기실 배너·결과 화면은 게스트도 본다.
 * 그 노출을 빼면 광고주에게 보고하는 수치가 실제보다 낮아진다.
 */
@Service
class AdEventService(
    private val adCampaignRepository: AdCampaignRepository,
    private val adEventRepository: AdEventRepository,
) {

    @Transactional
    fun record(campaignId: Long, type: AdEventType, principal: AuthPrincipal?) {
        // 집계 증가는 잠그고 읽는다 — 같은 광고에 노출이 몰리면 잠그지 않은 증가는 사라진다
        val campaign = adCampaignRepository.findByIdForUpdate(campaignId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "광고를 찾을 수 없습니다.")

        campaign.record(type)
        adEventRepository.save(
            AdEvent(
                campaignId = campaign.id,
                type = type,
                userId = (principal as? UserPrincipal)?.userId,
                participantId = (principal as? GuestPrincipal)?.participantId,
            ),
        )
    }
}
