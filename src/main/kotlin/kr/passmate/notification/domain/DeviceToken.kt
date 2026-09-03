package kr.passmate.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.time.LocalDateTime

enum class DevicePlatform { ANDROID, IOS, WEB }

/**
 * 푸시 토큰 (FCM/APNs). 토큰이 유일하다(uk_device_token).
 *
 * 한 사람이 기기를 여러 대 쓸 수 있어 회원당 여러 줄이 남는다.
 * 반대로 **같은 기기를 다른 계정이 쓸 수도 있어서** 토큰의 주인은 바뀔 수 있다 —
 * 계정을 갈아탄 기기에 옛 주인의 알림이 계속 가면 안 된다.
 */
@Entity
@Table(name = "device_token")
class DeviceToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    var platform: DevicePlatform,

    @Column(name = "token", nullable = false, length = TOKEN_MAX, updatable = false)
    val token: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    /** 마지막으로 이 토큰을 다시 등록한 시각. 오래 안 보인 토큰은 나중에 정리한다 */
    @Column(name = "last_seen_at")
    var lastSeenAt: LocalDateTime? = null
        protected set

    fun refresh(userId: Long, platform: DevicePlatform, at: LocalDateTime = LocalDateTime.now()) {
        this.userId = userId
        this.platform = platform
        this.lastSeenAt = at
    }

    companion object {
        const val TOKEN_MAX = 255
    }
}
