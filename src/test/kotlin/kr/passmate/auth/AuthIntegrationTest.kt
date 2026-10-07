package kr.passmate.auth

import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.auth.client.AppleOAuthClient
import kr.passmate.auth.client.GoogleOAuthClient
import kr.passmate.auth.client.SocialAccount
import kr.passmate.coin.repository.CoinWalletRepository
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.common.security.TextEncryptor
import kr.passmate.support.FakeAppleOAuthClient
import kr.passmate.support.FakeGoogleOAuthClient
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest : IntegrationTestSupport() {

    @TestConfiguration
    class FakeClientConfig {
        // 외부 Client 는 Fake 로 대체한다 — 테스트가 Google 을 호출하지 않는다
        @Bean
        @Primary
        fun fakeGoogleOAuthClient(): GoogleOAuthClient = FakeGoogleOAuthClient()

        @Bean
        @Primary
        fun fakeAppleOAuthClient(): AppleOAuthClient = FakeAppleOAuthClient()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var googleOAuthClient: GoogleOAuthClient
    @Autowired private lateinit var appleOAuthClient: AppleOAuthClient
    @Autowired private lateinit var coinWalletRepository: CoinWalletRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var textEncryptor: TextEncryptor

    private val fake: FakeGoogleOAuthClient get() = googleOAuthClient as FakeGoogleOAuthClient
    private val fakeApple: FakeAppleOAuthClient get() = appleOAuthClient as FakeAppleOAuthClient

    @BeforeEach
    fun setUp() {
        fake.reset()
        fake.registerIdToken(
            VALID_ID_TOKEN,
            SocialAccount(
                providerId = "google-sub-1",
                email = "hyerim@example.com",
                emailVerified = true,
                name = "혜림",
                pictureUrl = "https://img.example.com/1.png",
            ),
        )
        fakeApple.reset()
        fakeApple.registerIdToken(
            VALID_APPLE_ID_TOKEN,
            SocialAccount(
                providerId = "apple-sub-1",
                email = "seunghyuk@example.com",
                emailVerified = true,
                name = null,
                pictureUrl = null,
                clientId = APPLE_CLIENT_ID,
            ),
        )
        fakeApple.registerAuthorizationCode(VALID_APPLE_CODE, "apple-refresh-token")
        fakeApple.registerIdToken(
            RELAY_APPLE_ID_TOKEN,
            SocialAccount(
                providerId = "apple-sub-2",
                email = "x7k2m9q4ab@privaterelay.appleid.com",
                emailVerified = true,
                name = null,
                pictureUrl = null,
            ),
        )
    }

    @Test
    fun `미가입 계정으로 로그인하면 자동 가입되고 코인 지갑이 생긴다`() {
        val result = login(VALID_ID_TOKEN)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(true))
            .andExpect(jsonPath("$.user.nickname").value("혜림"))
            .andExpect(jsonPath("$.user.email").value("hyerim@example.com"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

        val userId = result.read<Int>("$.user.id").toLong()
        assertThat(coinWalletRepository.existsByUserId(userId)).isTrue()
    }

    @Test
    fun `같은 계정으로 다시 로그인하면 isNewUser 가 false 다`() {
        login(VALID_ID_TOKEN).andExpect(status().isOk)

        login(VALID_ID_TOKEN)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(false))
    }

    @Test
    fun `Apple 로 처음 로그인하면 앱이 보낸 이름으로 가입된다`() {
        appleLogin(VALID_APPLE_ID_TOKEN, name = "승혁")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(true))
            .andExpect(jsonPath("$.user.nickname").value("승혁"))
            .andExpect(jsonPath("$.user.email").value("seunghyuk@example.com"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `Apple 재로그인은 이름 없이도 같은 계정이다`() {
        appleLogin(VALID_APPLE_ID_TOKEN, name = "승혁").andExpect(status().isOk)

        appleLogin(VALID_APPLE_ID_TOKEN)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(false))
            .andExpect(jsonPath("$.user.nickname").value("승혁"))
    }

    @Test
    fun `Apple 이메일 가리기 중계 주소는 닉네임으로 쓰지 않는다`() {
        appleLogin(RELAY_APPLE_ID_TOKEN)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.nickname").value("패스메이트 회원"))
    }

    @Test
    fun `Apple 인가 코드를 함께 보내면 refresh token 을 암호화해 보관한다`() {
        appleLogin(VALID_APPLE_ID_TOKEN, code = VALID_APPLE_CODE).andExpect(status().isOk)

        val user = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-1")!!
        assertThat(user.appleRefreshToken).isNotEqualTo("apple-refresh-token")
        assertThat(textEncryptor.decrypt(user.appleRefreshToken!!)).isEqualTo("apple-refresh-token")
        assertThat(user.appleClientId).isEqualTo(APPLE_CLIENT_ID)
    }

    @Test
    fun `Apple 인가 코드 교환이 실패해도 로그인은 된다`() {
        appleLogin(VALID_APPLE_ID_TOKEN, code = "expired-code")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)

        val user = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-1")!!
        assertThat(user.appleRefreshToken).isNull()
    }

    @Test
    fun `Apple 키가 설정되지 않았으면 코드를 교환하지 않고 로그인만 한다`() {
        fakeApple.isTokenApiConfigured = false

        appleLogin(VALID_APPLE_ID_TOKEN, code = VALID_APPLE_CODE).andExpect(status().isOk)

        val user = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-1")!!
        assertThat(user.appleRefreshToken).isNull()
    }

    @Test
    fun `Apple 은 authorizationCode 만으로는 로그인할 수 없다`() {
        mockMvc.perform(
            post("/auth/login/{provider}", "apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"authorizationCode":"code"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `검증되지 않은 Apple 토큰은 401 이다`() {
        appleLogin("unknown-token")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SOCIAL_TOKEN_INVALID"))
    }

    @Test
    fun `지원하지 않는 provider 는 400 이다`() {
        mockMvc.perform(
            post("/auth/login/{provider}", "kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"$VALID_ID_TOKEN"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_PROVIDER"))
    }

    @Test
    fun `검증되지 않은 소셜 토큰은 401 이다`() {
        login("unknown-token")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("SOCIAL_TOKEN_INVALID"))
    }

    @Test
    fun `idToken 과 authorizationCode 를 함께 보내면 400 이다`() {
        mockMvc.perform(
            post("/auth/login/{provider}", "google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"a","authorizationCode":"b"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `리프레시 토큰으로 액세스 토큰을 재발급받는다`() {
        val refreshToken = login(VALID_ID_TOKEN).andReturn().read<String>("$.refreshToken")

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.expiresIn").isNumber)
    }

    @Test
    fun `액세스 토큰으로 재발급을 시도하면 401 이다`() {
        val accessToken = login(VALID_ID_TOKEN).andReturn().read<String>("$.accessToken")

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$accessToken"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
    }

    @Test
    fun `토큰 없이 인증이 필요한 API 를 부르면 401 이다`() {
        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `만료된 토큰은 403 이 아니라 401 TOKEN_EXPIRED 로 응답한다`() {
        // 403 으로 응답하면 클라이언트의 refresh 재시도가 발화하지 않는다
        val expired = jwtTokenProvider.issue(1L, false, Instant.now().minusSeconds(7200)).accessToken

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer $expired"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
    }

    @Test
    fun `로그인한 뒤 로그아웃하면 204 다`() {
        val accessToken = login(VALID_ID_TOKEN).andReturn().read<String>("$.accessToken")

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `만료된 리프레시 토큰으로는 재발급받을 수 없다`() {
        // 리프레시 유효기간(14일)보다 더 과거에 발급된 것으로 만든다
        val expired = jwtTokenProvider.issue(1L, false, Instant.now().minusSeconds(15 * 24 * 3600L)).refreshToken

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$expired"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
    }

    @Test
    fun `서명이 깨진 리프레시 토큰은 401 TOKEN_INVALID 다`() {
        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"not-a-jwt"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
    }

    @Test
    fun `게스트 토큰으로는 재발급받을 수 없다`() {
        val guestToken = jwtTokenProvider.issueGuestToken(participantId = 1L, roomId = 1L)

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$guestToken"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
    }

    @Test
    fun `dev-login 은 운영·테스트 프로파일에 존재하지 않는다`() {
        // @Profile("local","dev") 한정 — 다른 프로파일에 이 경로가 열려 있으면 인증 우회가 된다
        mockMvc.perform(
            post("/auth/dev-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"attacker"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.accessToken").doesNotExist())
    }

    private fun login(idToken: String) = mockMvc.perform(
        post("/auth/login/{provider}", "google")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"idToken":"$idToken"}"""),
    )

    private fun appleLogin(idToken: String, name: String? = null, code: String? = null) = mockMvc.perform(
        post("/auth/login/{provider}", "apple")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    mapOf("idToken" to idToken, "name" to name, "authorizationCode" to code),
                ),
            ),
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> MvcResult.read(path: String): T {
        val body = objectMapper.readTree(response.contentAsString)
        val key = path.removePrefix("$.")
        return key.split('.').fold(body) { node, name -> node.get(name) }
            .let { if (it.isNumber) it.intValue() else it.asText() } as T
    }

    companion object {
        private const val VALID_ID_TOKEN = "valid-google-id-token"
        private const val VALID_APPLE_ID_TOKEN = "valid-apple-id-token"
        private const val RELAY_APPLE_ID_TOKEN = "relay-apple-id-token"
        private const val VALID_APPLE_CODE = "valid-apple-code"
        private const val APPLE_CLIENT_ID = "org.sesacteamproject.passmate.Passmate"
    }
}
