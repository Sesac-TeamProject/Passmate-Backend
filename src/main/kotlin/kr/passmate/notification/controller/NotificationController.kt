package kr.passmate.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.notification.dto.DeviceTokenRequest
import kr.passmate.notification.dto.DeviceTokenResponse
import kr.passmate.notification.dto.NotificationSettingRequest
import kr.passmate.notification.dto.NotificationSettingResponse
import kr.passmate.notification.service.DeviceTokenService
import kr.passmate.notification.service.NotificationSettingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 알림 — C-02 v3 · M-12 마이페이지 알림 설정.
 */
@Tag(name = "마이페이지")
@RestController
@RequestMapping("/users/me")
class NotificationController(
    private val notificationSettingService: NotificationSettingService,
    private val deviceTokenService: DeviceTokenService,
) {

    @Operation(
        summary = "알림 설정 조회",
        description = "세션 시작·별점 요청·정산 완료 항목별 on/off. 설정한 적 없으면 전부 켜짐으로 시작한다.",
    )
    @GetMapping("/notification-settings")
    fun getSettings(@CurrentUser principal: UserPrincipal): NotificationSettingResponse =
        NotificationSettingResponse.from(notificationSettingService.getOrCreate(principal.userId))

    @Operation(
        summary = "알림 설정 변경",
        description = "세 항목을 한 번에 저장한다. 부분 갱신은 받지 않는다.",
    )
    @PutMapping("/notification-settings")
    fun updateSettings(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: NotificationSettingRequest,
    ): NotificationSettingResponse =
        NotificationSettingResponse.from(notificationSettingService.update(principal.userId, request))

    @Operation(
        summary = "푸시 토큰 등록",
        description = "앱 푸시용 FCM/APNs 토큰을 등록·갱신한다(같은 토큰은 덮어쓴다).",
    )
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerDevice(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: DeviceTokenRequest,
    ): DeviceTokenResponse =
        DeviceTokenResponse.from(deviceTokenService.register(principal.userId, request))
}
