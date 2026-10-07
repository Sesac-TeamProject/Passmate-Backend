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
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Apple identity token 을 로컬에서 검증한다 — 서명(JWKS)·만료·iss·aud 를 모두 확인한다.
 *
 * 로그인 자체는 ID 토큰 검증만으로 끝난다. .p8 키는 탈퇴 때 Apple 연결을 끊기(revoke) 위한 것이다 —
 * 폐기에는 refresh token 이 필요하고, 그건 로그인 때 앱이 준 인가 코드를 교환해야만 받을 수 있다.
 *
 * Apple 은 ID 토큰에 이름·프로필 사진을 싣지 않는다. 이름은 앱이 첫 인증 때만 받아
 * 요청 본문(name)으로 보내 주고, 사진은 없다.
 */
@Component
class AppleOAuthClientImpl(
    private val properties: AppleProperties,
    restClientBuilder: RestClient.Builder,
) : AppleOAuthClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = restClientBuilder.build()

    private val clientIds: List<String> = properties.clientIds.filter { it.isNotBlank() }

    private val clientSecretFactory = AppleClientSecretFactory(properties)

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

    override val isTokenApiConfigured: Boolean
        get() = properties.isTokenApiConfigured

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

    override fun exchangeAuthorizationCode(code: String, clientId: String): String {
        val body = postForm(
            uri = properties.tokenUri,
            clientId = clientId,
            fields = mapOf("code" to code, "grant_type" to "authorization_code"),
        )
        return body?.get("refresh_token") as? String
            ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 응답에 refresh_token 이 없습니다.")
    }

    override fun revokeRefreshToken(refreshToken: String, clientId: String) {
        postForm(
            uri = properties.revokeUri,
            clientId = clientId,
            fields = mapOf("token" to refreshToken, "token_type_hint" to "refresh_token"),
        )
    }

    private fun postForm(uri: String, clientId: String, fields: Map<String, String>): Map<String, Any?>? {
        if (!isTokenApiConfigured) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 토큰 API 키(.p8)가 설정되지 않았습니다.")
        }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("client_secret", clientSecretFactory.create(clientId))
            fields.forEach { (key, value) -> add(key, value) }
        }
        return try {
            restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        } catch (e: RestClientException) {
            log.warn("Apple 토큰 API 호출 실패 uri={}", uri, e)
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, cause = e)
        }
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
            clientId = audience.orEmpty().firstOrNull { it in clientIds },
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
