package kr.passmate.common.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * 인증된 주체. 회원(UserPrincipal)과 게스트(GuestPrincipal) 둘 다 여기에 들어온다.
 * 컨트롤러는 @CurrentUser 로 받는데, 파라미터 타입을 UserPrincipal 로 두면 회원만,
 * AuthPrincipal 로 두면 게스트도 받는다.
 */
sealed interface AuthPrincipal {
    fun authorities(): List<GrantedAuthority>
}

/** 로그인한 회원. */
data class UserPrincipal(
    val userId: Long,
    val isAdmin: Boolean,
) : AuthPrincipal {
    override fun authorities(): List<GrantedAuthority> = buildList {
        add(SimpleGrantedAuthority(ROLE_USER))
        if (isAdmin) add(SimpleGrantedAuthority(ROLE_ADMIN))
    }

    companion object {
        const val ROLE_USER = "ROLE_USER"
        const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}

/**
 * 가입 없이 PIN 으로 입장한 게스트.
 * 토큰이 **자기가 입장한 방 하나**에만 유효하다 — 다른 방의 API 는 이 토큰으로 부를 수 없다.
 */
data class GuestPrincipal(
    val participantId: Long,
    val roomId: Long,
) : AuthPrincipal {
    override fun authorities(): List<GrantedAuthority> =
        listOf(SimpleGrantedAuthority(ROLE_GUEST))

    companion object {
        const val ROLE_GUEST = "ROLE_GUEST"
    }
}
