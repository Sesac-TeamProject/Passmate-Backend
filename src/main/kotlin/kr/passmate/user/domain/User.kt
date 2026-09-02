package kr.passmate.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import java.time.LocalDateTime

/**
 * 소셜 계정 단일 회원. 선생님·학생 공용 단일 유형이며 역할은 계정에 고정되지 않는다(FR-001).
 * 상태 전이는 전부 이 클래스의 메서드로만 한다.
 */
@Entity
@Table(name = "`user`")
class User(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    val provider: AuthProvider,

    @Column(name = "provider_id", nullable = false, length = 100, updatable = false)
    val providerId: String,

    @Column(name = "email", length = 255)
    var email: String? = null,

    @Column(name = "nickname", nullable = false, length = 30)
    var nickname: String,

    @Column(name = "profile_image_url", length = 500)
    var profileImageUrl: String? = null,

    @Column(name = "default_avatar_id", length = 30)
    var defaultAvatarId: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "is_admin", nullable = false)
    var isAdmin: Boolean = false
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    val isActive: Boolean
        get() = status == UserStatus.ACTIVE

    /** 로그인 성공 시각 기록. */
    fun recordLogin(at: LocalDateTime = LocalDateTime.now()) {
        lastLoginAt = at
    }

    /**
     * 소셜 프로필이 바뀌었으면 반영한다. 사용자가 앱에서 직접 바꾼 닉네임은 덮어쓰지 않는다 —
     * 닉네임은 최초 가입 때만 소셜 값으로 초기화한다(FR-001).
     */
    fun syncSocialProfile(email: String?, profileImageUrl: String?) {
        email?.let { this.email = it }
        profileImageUrl?.let { this.profileImageUrl = it }
    }

    /**
     * 사용자가 마이페이지에서 직접 고친 값(FR-064).
     * 소셜 동기화(syncSocialProfile)가 이 값을 덮어쓰지 않는다 — 닉네임은 가입 때만 소셜 값을 쓴다.
     */
    fun updateProfile(nickname: String, profileImageUrl: String?, defaultAvatarId: String?) {
        val trimmed = nickname.trim()
        if (trimmed.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 비어 있을 수 없습니다.")
        }
        this.nickname = trimmed.take(NICKNAME_MAX_LENGTH)
        this.profileImageUrl = profileImageUrl
        this.defaultAvatarId = defaultAvatarId
    }

    /** 제재로 정지. 로그인·입장이 차단된다. */
    fun suspend() {
        check(status != UserStatus.DELETED) { "탈퇴한 계정은 정지할 수 없습니다." }
        status = UserStatus.SUSPENDED
    }

    /** 제재 해제 — 즉시 복구된다(SC 기준). */
    fun release() {
        check(status == UserStatus.SUSPENDED) { "정지 상태가 아닙니다." }
        status = UserStatus.ACTIVE
    }

    /**
     * 탈퇴(soft delete) + 개인정보 파기.
     *
     * 행을 지우지 않는 이유는 참여·개설 기록이 이 사용자를 FK 로 가리키기 때문이다.
     * 대신 **누구였는지를 지운다** — 이메일·프로필 이미지를 비우고 닉네임을 익명으로 바꾼다.
     * user.nickname 에는 유니크 제약이 없어 여러 탈퇴 계정이 같은 값을 가져도 된다.
     */
    fun withdraw(at: LocalDateTime = LocalDateTime.now()) {
        status = UserStatus.DELETED
        deletedAt = at
        email = null
        profileImageUrl = null
        defaultAvatarId = null
        nickname = WITHDRAWN_NICKNAME
    }

    companion object {
        /** 닉네임 컬럼 길이 상한 — 소셜 프로필 이름이 더 길면 잘라서 넣는다. */
        const val NICKNAME_MAX_LENGTH = 30

        /** 탈퇴한 계정의 표시 이름. 지난 세션 랭킹에도 이 이름으로 남는다 */
        const val WITHDRAWN_NICKNAME = "탈퇴한 사용자"
    }
}
