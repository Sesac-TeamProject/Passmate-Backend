package kr.passmate.notification.service

import kr.passmate.notification.domain.DeviceToken
import kr.passmate.notification.dto.DeviceTokenRequest
import kr.passmate.notification.repository.DeviceTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 푸시 토큰 등록 (FR-065).
 *
 * 발송은 아직 없다 — Firebase 자격증명이 준비되면 이 토큰들을 받아 쓴다.
 * 등록 자체는 외부 설정과 무관해서 먼저 열어 둔다.
 */
@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
) {

    /**
     * 등록·갱신. 앱은 실행할 때마다 같은 토큰을 다시 보내므로 upsert 다 —
     * 매번 새 행을 쌓으면 한 기기에 같은 알림이 여러 번 간다.
     *
     * 이미 있는 토큰이면 주인을 지금 회원으로 바꾼다. 같은 기기를 다른 계정이 쓰기 시작했는데
     * 옛 주인 앞으로 계속 보내면 남의 알림이 남의 기기에 뜬다.
     */
    @Transactional
    fun register(userId: Long, request: DeviceTokenRequest): DeviceToken {
        val token = request.token.trim()
        val existing = deviceTokenRepository.findByToken(token)
        if (existing != null) {
            existing.refresh(userId, request.platform)
            return existing
        }
        return deviceTokenRepository.save(
            DeviceToken(userId = userId, platform = request.platform, token = token)
                .apply { refresh(userId, request.platform) },
        )
    }

    /** 이 회원에게 보낼 기기 목록. 발송하는 쪽이 쓴다. */
    @Transactional(readOnly = true)
    fun tokensOf(userId: Long): List<String> =
        deviceTokenRepository.findAllByUserId(userId).map { it.token }
}
