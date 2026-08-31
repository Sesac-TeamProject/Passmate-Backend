package kr.passmate.common.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/** 인증된 주체. 컨트롤러는 @CurrentUser 로 이걸 받는다. */
data class UserPrincipal(
    val userId: Long,
    val isAdmin: Boolean,
) {
    fun authorities(): List<GrantedAuthority> = buildList {
        add(SimpleGrantedAuthority(ROLE_USER))
        if (isAdmin) add(SimpleGrantedAuthority(ROLE_ADMIN))
    }

    companion object {
        const val ROLE_USER = "ROLE_USER"
        const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}
