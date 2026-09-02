package kr.passmate.notification.repository

import kr.passmate.notification.domain.DeviceToken
import kr.passmate.notification.domain.NotificationSetting
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingRepository : JpaRepository<NotificationSetting, Long> {

    fun findByUserId(userId: Long): NotificationSetting?
}

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {

    fun findByToken(token: String): DeviceToken?

    fun findAllByUserId(userId: Long): List<DeviceToken>
}
