package kr.passmate.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.passmate.notification.domain.DevicePlatform
import kr.passmate.notification.domain.DeviceToken
import kr.passmate.notification.domain.NotificationSetting
import java.time.LocalDateTime

/**
 * 알림 설정 (FR-065). 세 항목 모두 필수로 받는다 —
 * 부분 갱신을 허용하면 화면이 토글 하나를 끄면서 나머지를 실수로 되돌릴 수 있다.
 *
 * 타입이 `Boolean?` 인 것은 **빠진 필드를 잡아내기 위해서**다. non-null `Boolean` 으로 두면
 * Jackson 이 없는 값을 false 로 채워 넣어 "끄겠다"와 "안 보냈다"가 구분되지 않는다.
 * 실제 null 은 @NotNull 이 400 으로 막으므로 서비스에는 값이 있는 채로만 들어온다.
 */
@Schema(description = "알림 설정 변경")
data class NotificationSettingRequest(
    @field:Schema(description = "참여 예정인 방의 세션이 시작될 때")
    @field:NotNull(message = "sessionStart 는 필수입니다.")
    val sessionStart: Boolean?,

    @field:Schema(description = "답안을 낸 세션이 끝나 평가를 남길 수 있을 때")
    @field:NotNull(message = "ratingRequest 는 필수입니다.")
    val ratingRequest: Boolean?,

    @field:Schema(description = "월 정산이 지급됐을 때")
    @field:NotNull(message = "settlementDone 는 필수입니다.")
    val settlementDone: Boolean?,
)

@Schema(description = "알림 설정")
data class NotificationSettingResponse(
    val sessionStart: Boolean,
    val ratingRequest: Boolean,
    val settlementDone: Boolean,
) {
    companion object {
        fun from(setting: NotificationSetting) = NotificationSettingResponse(
            sessionStart = setting.sessionStart,
            ratingRequest = setting.ratingRequest,
            settlementDone = setting.settlementDone,
        )
    }
}

@Schema(description = "푸시 토큰 등록")
data class DeviceTokenRequest(
    @field:NotNull
    val platform: DevicePlatform,

    @field:Schema(description = "FCM/APNs 토큰")
    @field:NotBlank
    @field:Size(max = DeviceToken.TOKEN_MAX)
    val token: String,
)

@Schema(description = "등록된 푸시 토큰")
data class DeviceTokenResponse(
    val id: Long,
    val platform: DevicePlatform,
    val lastSeenAt: LocalDateTime?,
) {
    companion object {
        /** 토큰 값 자체는 돌려주지 않는다 — 보낸 쪽이 이미 갖고 있고, 남기면 로그에 새어 나간다. */
        fun from(device: DeviceToken) = DeviceTokenResponse(
            id = device.id,
            platform = device.platform,
            lastSeenAt = device.lastSeenAt,
        )
    }
}
