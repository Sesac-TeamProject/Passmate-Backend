package kr.passmate.auth.client

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

/**
 * Apple identity token 을 로컬에서 검증한다 — 서명(JWKS)·만료·iss·aud 를 모두 확인한다.
 * 네이티브(iOS) 로그인만 받으므로 인가 코드 교환·client secret(.p8)은 필요 없다.
 *
 * Apple 은 ID 토큰에 이름·프로필 사진을 싣지 않는다. 이름은 앱이 첫 인증 때만 받아
 * 요청 본문(name)으로 보내 주고, 사진은 없다.
 */
@Component
class AppleOAuthClientImpl(
    private val properties: AppleProperties,
) : AppleOAuthClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val clientIds: List<String> = properties.clientIds.filter { it.isNotBlank() }

    private val decoder: NimbusJwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator(),
                    JwtIssuerValidator(properties.issuer),
                    AudienceValidator(clientIds),
                ),
            )
        }

    override fun verifyIdToken(idToken: String): SocialAccount {
        if (clientIds.isEmpty()) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 클라이언트 ID(번들 ID)가 설정되지 않았습니다.")
        }
        val jwt = try {
            decoder.decode(idToken)
        } catch (e: JwtException) {
            log.warn("Apple ID 토큰 검증 실패", e)
            throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, cause = e)
        }
        return jwt.toSocialAccount()
    }

    private fun Jwt.toSocialAccount(): SocialAccount {
        val providerId = subject
            ?: throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, "Apple 계정 식별자가 없습니다.")
        return SocialAccount(
            providerId = providerId,
            email = getClaimAsString("email"),
            emailVerified = getClaim<Any?>("email_verified").toBooleanClaim(),
            name = null,
            pictureUrl = null,
        )
    }

    /** Apple 은 email_verified 를 문자열 "true" 로 보낼 때가 있다. */
    private fun Any?.toBooleanClaim(): Boolean = when (this) {
        is Boolean -> this
        is String -> equals("true", ignoreCase = true)
        else -> false
    }

    private class AudienceValidator(private val clientIds: List<String>) : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (token.audience.orEmpty().any { it in clientIds }) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_audience", "이 서비스로 발급된 토큰이 아닙니다.", null),
                )
            }
    }
}
