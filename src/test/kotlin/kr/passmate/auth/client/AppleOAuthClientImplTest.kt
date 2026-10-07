package kr.passmate.auth.client

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

/**
 * Apple ID 토큰 검증 — 서명·iss·aud·만료를 실제 JWT 로 확인한다.
 * Apple JWKS 대신 루프백 HTTP 서버가 테스트 키를 내려주므로 외부로 나가는 요청은 없다.
 */
class AppleOAuthClientImplTest {

    private val appleKey: RSAKey = RSAKeyGenerator(2048).keyID("apple-test").generate()
    private val strangerKey: RSAKey = RSAKeyGenerator(2048).keyID("apple-test").generate()

    private lateinit var jwksServer: HttpServer
    private lateinit var client: AppleOAuthClientImpl

    @BeforeEach
    fun setUp() {
        val jwks = JWKSet(appleKey.toPublicJWK()).toString().toByteArray()
        jwksServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/auth/keys") { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, jwks.size.toLong())
                exchange.responseBody.use { it.write(jwks) }
            }
            start()
        }
        client = AppleOAuthClientImpl(
            AppleProperties(
                clientIds = listOf(BUNDLE_ID),
                jwkSetUri = "http://127.0.0.1:${jwksServer.address.port}/auth/keys",
                issuer = ISSUER,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        jwksServer.stop(0)
    }

    @Test
    fun `Apple 이 서명한 토큰이면 sub 와 이메일을 돌려준다`() {
        val account = client.verifyIdToken(token())

        assertThat(account.providerId).isEqualTo("001234.abcd.5678")
        assertThat(account.email).isEqualTo("user@privaterelay.appleid.com")
        assertThat(account.emailVerified).isTrue()
        assertThat(account.name).isNull()
        assertThat(account.pictureUrl).isNull()
    }

    @Test
    fun `다른 키로 서명한 토큰은 거절한다`() {
        assertSocialTokenInvalid(token(signer = strangerKey))
    }

    @Test
    fun `다른 앱 번들 ID 로 발급된 토큰은 거절한다`() {
        assertSocialTokenInvalid(token(audience = "com.example.other"))
    }

    @Test
    fun `Apple 이 아닌 발급자의 토큰은 거절한다`() {
        assertSocialTokenInvalid(token(issuer = "https://accounts.google.com"))
    }

    @Test
    fun `만료된 토큰은 거절한다`() {
        assertSocialTokenInvalid(token(expiresAt = Instant.now().minusSeconds(3600)))
    }

    @Test
    fun `번들 ID 가 설정되지 않았으면 외부 연동 오류다`() {
        val unconfigured = AppleOAuthClientImpl(
            AppleProperties(clientIds = listOf(""), jwkSetUri = "http://127.0.0.1:1/keys", issuer = ISSUER),
        )

        assertThatThrownBy { unconfigured.verifyIdToken(token()) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.EXTERNAL_API_ERROR)
    }

    private fun assertSocialTokenInvalid(idToken: String) {
        assertThatThrownBy { client.verifyIdToken(idToken) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID)
    }

    private fun token(
        signer: RSAKey = appleKey,
        audience: String = BUNDLE_ID,
        issuer: String = ISSUER,
        expiresAt: Instant = Instant.now().plusSeconds(600),
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("001234.abcd.5678")
            .issueTime(Date.from(expiresAt.minusSeconds(600)))
            .expirationTime(Date.from(expiresAt))
            .claim("email", "user@privaterelay.appleid.com")
            // Apple 은 이 값을 문자열로 보낸다
            .claim("email_verified", "true")
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.keyID).type(JOSEObjectType.JWT).build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(signer)) }.serialize()
    }

    companion object {
        private const val BUNDLE_ID = "org.sesacteamproject.passmate.Passmate"
        private const val ISSUER = "https://appleid.apple.com"
    }
}
