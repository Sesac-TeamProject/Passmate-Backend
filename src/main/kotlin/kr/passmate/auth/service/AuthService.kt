package kr.passmate.auth.service

import kr.passmate.auth.client.GoogleAccount
import kr.passmate.auth.client.GoogleOAuthClient
import kr.passmate.auth.dto.LoginResponse
import kr.passmate.auth.dto.SocialLoginRequest
import kr.passmate.auth.dto.TokenRefreshRequest
import kr.passmate.auth.dto.TokenResponse
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 회원가입·로그인은 Google 소셜 로그인으로 통일한다(FR-001).
 *
 * 이 클래스에는 @Transactional 을 붙이지 않는다 — Google 호출이 트랜잭션 안에 들어가면
 * 외부 응답이 늦어질 때 커넥션을 잡아먹는다. 검증을 먼저 끝내고 결과만 UserService 에 넘긴다.
 */
@Service
class AuthService(
    private val googleOAuthClient: GoogleOAuthClient,
    private val userService: UserService,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 소셜 로그인. 미가입이면 그 자리에서 가입시키고 isNewUser 로 알려준다. */
    fun login(provider: AuthProvider, request: SocialLoginRequest): LoginResponse {
        val account = verifySocialAccount(provider, request)

        val outcome = userService.loginOrRegister(
            provider = provider,
            providerId = account.providerId,
            email = account.email,
            name = account.name,
            profileImageUrl = account.pictureUrl,
        )

        if (outcome.isNewUser) {
            log.info("신규 가입 — userId={} provider={}", outcome.user.id, provider)
        }
        return LoginResponse.of(outcome, jwtTokenProvider.issue(outcome.user.id, outcome.user.isAdmin))
    }

    /**
     * 리프레시 토큰으로 액세스 토큰을 재발급한다.
     * 매번 계정 상태를 다시 확인하므로, 제재된 계정은 토큰이 살아 있어도 여기서 막힌다.
     */
    fun refresh(request: TokenRefreshRequest): TokenResponse {
        val principal = jwtTokenProvider.parseRefreshToken(request.refreshToken)
        val user = userService.getActiveUser(principal.userId)
        return TokenResponse.of(jwtTokenProvider.issue(user.id, user.isAdmin))
    }

    /**
     * 로그아웃.
     *
     * Redis 후순위 결정에 따라 토큰은 stateless 다 — 서버에 저장된 것이 없어 즉시 무효화할 수 없다.
     * 클라이언트가 액세스·리프레시 토큰을 폐기하는 것이 로그아웃이고, 서버는 그 사실만 남긴다.
     * Redis 도입 시 여기에서 jti 를 블랙리스트에 넣으면 API 계약은 그대로 둘 수 있다.
     */
    fun logout(userId: Long) {
        log.info("로그아웃 — userId={}", userId)
    }

    private fun verifySocialAccount(provider: AuthProvider, request: SocialLoginRequest): GoogleAccount =
        when (provider) {
            AuthProvider.GOOGLE -> when {
                !request.idToken.isNullOrBlank() ->
                    googleOAuthClient.verifyIdToken(request.idToken)

                !request.authorizationCode.isNullOrBlank() ->
                    googleOAuthClient.exchangeAuthorizationCode(request.authorizationCode, request.redirectUri)

                else -> throw BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "idToken 또는 authorizationCode 가 필요합니다.",
                )
            }
        }
}
