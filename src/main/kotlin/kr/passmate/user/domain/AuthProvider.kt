package kr.passmate.user.domain

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode

/**
 * 소셜 로그인 제공자. 현재 허용값은 GOOGLE 하나뿐이고,
 * APPLE 은 앱 스토어 심사 요건이 생기면 추가한다(기능 명세서 v2 FR-001).
 */
enum class AuthProvider {
    GOOGLE,
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
