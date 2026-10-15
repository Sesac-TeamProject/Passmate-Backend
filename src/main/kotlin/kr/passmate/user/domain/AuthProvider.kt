package kr.passmate.user.domain

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode

/**
 * 소셜 로그인 제공자(기능 명세서 v2 FR-001).
 * APPLE 은 iOS 앱 전용이다 — 제3자 로그인을 제공하는 앱은 Apple 로그인도 함께 제공해야 한다(App Store 심사 지침 4.8).
 */
enum class AuthProvider {
    GOOGLE,
    APPLE,
    ;

    companion object {
        /** 경로 변수 `{provider}` 를 해석한다. 대소문자는 가리지 않는다. */
        fun from(value: String): AuthProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw BusinessException(
                    ErrorCode.UNSUPPORTED_PROVIDER,
                    "지원하지 않는 로그인 방식입니다: $value",
                )
    }
}
