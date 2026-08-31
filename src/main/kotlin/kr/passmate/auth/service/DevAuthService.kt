package kr.passmate.auth.service

import kr.passmate.auth.dto.LoginResponse
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * Google OAuth 클라이언트가 준비되기 전에 웹·앱 팀이 API 를 붙여볼 수 있게 하는 개발용 로그인.
 * local · dev 프로파일에서만 빈이 만들어진다 — 운영에는 존재하지 않는다.
 */
@Profile("local", "dev")
@Service
class DevAuthService(
    private val userService: UserService,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    fun login(key: String, nickname: String?, email: String?): LoginResponse {
        val outcome = userService.loginOrRegister(
            provider = AuthProvider.GOOGLE,
            providerId = "$DEV_PROVIDER_ID_PREFIX$key",
            email = email ?: "$key@dev.passmate.local",
            name = nickname ?: "개발계정 $key",
            profileImageUrl = null,
        )
        return LoginResponse.of(outcome, jwtTokenProvider.issue(outcome.user.id, outcome.user.isAdmin))
    }

    companion object {
        /** 실제 Google sub 와 절대 겹치지 않도록 접두사를 붙인다. */
        const val DEV_PROVIDER_ID_PREFIX = "dev-"
    }
}
