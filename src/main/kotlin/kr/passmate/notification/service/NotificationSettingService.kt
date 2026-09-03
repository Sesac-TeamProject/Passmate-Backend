package kr.passmate.notification.service

import kr.passmate.notification.domain.NotificationSetting
import kr.passmate.notification.dto.NotificationSettingRequest
import kr.passmate.notification.repository.NotificationSettingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 설정 (FR-065, C-02 v3 · M-12).
 *
 * 행은 회원이 설정 화면을 처음 열 때 만들어진다 — 가입 시점에 미리 넣어 두면
 * 이미 가입한 회원에게는 없는 채로 남고, 기본값이 바뀔 때 마이그레이션이 필요해진다.
 */
@Service
class NotificationSettingService(
    private val settingRepository: NotificationSettingRepository,
) {

    /** 없으면 기본값(전부 켜짐)으로 만들어 준다. */
    @Transactional
    fun getOrCreate(userId: Long): NotificationSetting =
        settingRepository.findByUserId(userId)
            ?: settingRepository.save(NotificationSetting(userId = userId))

    @Transactional
    fun update(userId: Long, request: NotificationSettingRequest): NotificationSetting =
        getOrCreate(userId).apply {
            // @NotNull 이 앞에서 걸러 주므로 여기 도달하면 세 값 모두 들어 있다
            update(request.sessionStart!!, request.ratingRequest!!, request.settlementDone!!)
        }

    /**
     * 이 회원이 그 알림을 받기로 했는지. 발송하는 쪽(세션 시작·평가 요청·정산)이 물어본다.
     * 설정 행이 아직 없으면 기본값대로 **받는 것으로** 본다.
     */
    @Transactional(readOnly = true)
    fun allows(userId: Long, kind: NotificationKind): Boolean {
        val setting = settingRepository.findByUserId(userId) ?: return true
        return when (kind) {
            NotificationKind.SESSION_START -> setting.sessionStart
            NotificationKind.RATING_REQUEST -> setting.ratingRequest
            NotificationKind.SETTLEMENT_DONE -> setting.settlementDone
        }
    }
}

/** 푸시 3종 (FR-065). 인앱 알림함은 화면에 없어 범위 밖이다. */
enum class NotificationKind { SESSION_START, RATING_REQUEST, SETTLEMENT_DONE }
