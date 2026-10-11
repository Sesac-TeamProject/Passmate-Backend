package kr.passmate.auth.client

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSAVerifier
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
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Apple ID 토큰 검증 — 서명·iss·aud·만료를 실제 JWT 로 확인한다.
 * Apple JWKS 대신 루프백 HTTP 서버가 테스트 키를 내려주므로 외부로 나가는 요청은 없다.
 * 토큰 API(/auth/token·/auth/revoke)는 MockRestServiceServer 로 받고, client secret 은 테스트 .p8 공개키로 검증한다.
 */
class AppleOAuthClientImplTest {

    private val appleKey: RSAKey = RSAKeyGenerator(2048).keyID("apple-test").generate()
    private val strangerKey: RSAKey = RSAKeyGenerator(2048).keyID("apple-test").generate()

    private val p8KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    private lateinit var jwksServer: HttpServer
    private lateinit var tokenServer: MockRestServiceServer
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
        val builder = RestClient.builder()
        tokenServer = MockRestServiceServer.bindTo(builder).build()
        client = AppleOAuthClientImpl(properties(), builder)
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
        val unconfigured = AppleOAuthClientImpl(properties(clientIds = listOf("")), RestClient.builder())

        assertThatThrownBy { unconfigured.verifyIdToken(token()) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.EXTERNAL_API_ERROR)
    }

    @Test
    fun `검증한 계정에 토큰이 발급된 클라이언트 ID 를 싣는다`() {
        assertThat(client.verifyIdToken(token()).clientId).isEqualTo(BUNDLE_ID)
    }

    @Test
    fun `인가 코드를 교환하면 refresh token 을 돌려준다`() {
        var form = emptyMap<String, String>()
        tokenServer.expect(requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect { form = (it as MockClientHttpRequest).bodyAsString.toForm() }
            .andRespond(withSuccess("""{"refresh_token":"r.apple","id_token":"x"}""", MediaType.APPLICATION_JSON))

        val refreshToken = client.exchangeAuthorizationCode("auth-code", BUNDLE_ID)

        tokenServer.verify()
        assertThat(refreshToken).isEqualTo("r.apple")
        assertThat(form).containsEntry("client_id", BUNDLE_ID)
            .containsEntry("code", "auth-code")
            .containsEntry("grant_type", "authorization_code")
        assertClientSecret(form.getValue("client_secret"))
    }

    @Test
    fun `refresh token 을 폐기 요청한다`() {
        var form = emptyMap<String, String>()
        tokenServer.expect(requestTo(REVOKE_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect { form = (it as MockClientHttpRequest).bodyAsString.toForm() }
            .andRespond(withSuccess())

        client.revokeRefreshToken("r.apple", BUNDLE_ID)

        tokenServer.verify()
        assertThat(form).containsEntry("client_id", BUNDLE_ID)
            .containsEntry("token", "r.apple")
            .containsEntry("token_type_hint", "refresh_token")
        assertClientSecret(form.getValue("client_secret"))
    }

    @Test
    fun `Apple 이 코드를 거절하면 외부 연동 오류다`() {
        tokenServer.expect(requestTo(TOKEN_URI))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("""{"error":"invalid_grant"}"""))

        assertThatThrownBy { client.exchangeAuthorizationCode("expired", BUNDLE_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.EXTERNAL_API_ERROR)
    }

    @Test
    fun `p8 키가 없으면 Apple 을 부르지 않고 외부 연동 오류다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val unconfigured = AppleOAuthClientImpl(properties(privateKey = ""), builder)

        assertThat(unconfigured.isTokenApiConfigured).isFalse()
        assertThatThrownBy { unconfigured.revokeRefreshToken("r.apple", BUNDLE_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.EXTERNAL_API_ERROR)
        server.verify()
    }

    @Test
    fun `p8 키는 PEM 그대로도 한 줄 본문으로도 읽는다`() {
        val body = Base64.getEncoder().encodeToString(p8KeyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n${body.chunked(64).joinToString("\n")}\n-----END PRIVATE KEY-----"
        val escaped = pem.replace("\n", "\\n")

        listOf(pem, escaped, body).forEach {
            assertThat(AppleClientSecretFactory.parsePrivateKey(it).encoded).isEqualTo(p8KeyPair.private.encoded)
        }
    }

    // Apple 이 요구하는 client secret 규격 — ES256, kid=키 ID, iss=팀 ID, sub=번들 ID, aud=Apple
    private fun assertClientSecret(secret: String) {
        val jwt = SignedJWT.parse(secret)

        assertThat(jwt.verify(ECDSAVerifier(p8KeyPair.public as ECPublicKey))).isTrue()
        assertThat(jwt.header.algorithm).isEqualTo(JWSAlgorithm.ES256)
        assertThat(jwt.header.keyID).isEqualTo(KEY_ID)
        assertThat(jwt.jwtClaimsSet.issuer).isEqualTo(TEAM_ID)
        assertThat(jwt.jwtClaimsSet.subject).isEqualTo(BUNDLE_ID)
        assertThat(jwt.jwtClaimsSet.audience).containsExactly(ISSUER)
        assertThat(jwt.jwtClaimsSet.expirationTime).isAfter(Date())
    }

    private fun String.toForm(): Map<String, String> =
        split('&').associate { pair ->
            val (key, value) = pair.split('=', limit = 2)
            URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun properties(
        clientIds: List<String> = listOf(BUNDLE_ID),
        privateKey: String = Base64.getEncoder().encodeToString(p8KeyPair.private.encoded),
    ) = AppleProperties(
        clientIds = clientIds,
        jwkSetUri = "http://127.0.0.1:${jwksServer.address.port}/auth/keys",
        issuer = ISSUER,
        tokenUri = TOKEN_URI,
        revokeUri = REVOKE_URI,
        teamId = TEAM_ID,
        keyId = KEY_ID,
        privateKey = privateKey,
    )

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
        private const val TOKEN_URI = "http://127.0.0.1:1/auth/token"
        private const val REVOKE_URI = "http://127.0.0.1:1/auth/revoke"
        private const val TEAM_ID = "TEAM123456"
        private const val KEY_ID = "KEY1234567"
    }
}
