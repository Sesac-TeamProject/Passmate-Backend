package kr.passmate.auth.client

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Google ID 토큰을 로컬에서 검증한다 — 서명(JWKS)·만료·iss·aud 를 모두 확인한다.
 * 공개키 캐싱과 로테이션은 NimbusJwtDecoder 가 처리하므로 로그인마다 Google 을 호출하지 않는다.
 */
@Component
class GoogleOAuthClientImpl(
    private val properties: GoogleProperties,
    restClientBuilder: RestClient.Builder,
) : GoogleOAuthClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = restClientBuilder.build()

    private val decoder: NimbusJwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator(),
                    IssuerValidator(properties.issuers),
                    AudienceValidator(properties.clientId),
                ),
            )
        }

    override fun verifyIdToken(idToken: String): GoogleAccount {
        if (properties.clientId.isBlank()) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google 클라이언트 ID가 설정되지 않았습니다.")
        }
        val jwt = try {
            decoder.decode(idToken)
        } catch (e: JwtException) {
            log.warn("Google ID 토큰 검증 실패", e)
            throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, cause = e)
        }
        return jwt.toGoogleAccount()
    }

    override fun exchangeAuthorizationCode(code: String, redirectUri: String?): GoogleAccount {
        if (properties.clientSecret.isBlank()) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google 클라이언트 시크릿이 설정되지 않았습니다.")
        }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
            add("redirect_uri", redirectUri?.takeIf { it.isNotBlank() } ?: properties.redirectUri)
            add("grant_type", "authorization_code")
        }

        val body = try {
            restClient.post()
                .uri(properties.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        } catch (e: RestClientException) {
            log.warn("Google 인가 코드 교환 실패", e)
            throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, cause = e)
        }

        val idToken = body?.get("id_token") as? String
            ?: throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, "Google 응답에 id_token 이 없습니다.")
        return verifyIdToken(idToken)
    }

    private fun Jwt.toGoogleAccount(): GoogleAccount {
        val providerId = subject
            ?: throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, "Google 계정 식별자가 없습니다.")
        return GoogleAccount(
            providerId = providerId,
            email = getClaimAsString("email"),
            emailVerified = getClaim<Any?>("email_verified").toBooleanClaim(),
            name = getClaimAsString("name"),
            pictureUrl = getClaimAsString("picture"),
        )
    }

    /** Google 은 email_verified 를 boolean 으로도, 문자열로도 보낸다. */
    private fun Any?.toBooleanClaim(): Boolean = when (this) {
        is Boolean -> this
        is String -> equals("true", ignoreCase = true)
        else -> false
    }

    private class IssuerValidator(private val allowed: List<String>) : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (token.issuer?.toString() in allowed) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_issuer", "허용되지 않은 iss 입니다: ${token.issuer}", null),
                )
            }
    }

    private class AudienceValidator(private val clientId: String) : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult =
            if (clientId.isNotBlank() && token.audience.contains(clientId)) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_audience", "이 서비스로 발급된 토큰이 아닙니다.", null),
                )
            }
    }
}
