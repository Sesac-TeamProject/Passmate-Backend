package kr.passmate.auth.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kr.passmate.auth.client.AppleOAuthClient
import kr.passmate.common.event.UserWithdrawnEvent
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.EncryptionProperties
import kr.passmate.common.security.TextEncryptor
import kr.passmate.support.FakeAppleOAuthClient
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

/**
 * Apple refresh token 보관·폐기. 어느 쪽이 실패해도 로그인·탈퇴 본 흐름으로 예외가 새지 않아야 한다.
 */
class AppleTokenServiceTest {

    private val apple = FakeAppleOAuthClient()

    private val encryptor = TextEncryptor(EncryptionProperties("test-encryption-key-for-unit-tests-32bytes!"))

    private val userService = mockk<UserService>(relaxed = true)

    private val service = AppleTokenService(apple, encryptor, userService)

    @Test
    fun `로그인 때 받은 refresh token 을 암호화해 보관한다`() {
        val stored = slot<String>()
        every { userService.linkAppleRefreshToken(1L, capture(stored), CLIENT_ID) } returns Unit
        apple.registerAuthorizationCode("code", "r.apple")

        service.linkOnLogin(1L, "code", CLIENT_ID)

        assertThat(stored.captured).isNotEqualTo("r.apple")
        assertThat(encryptor.decrypt(stored.captured)).isEqualTo("r.apple")
    }

    @Test
    fun `코드가 없거나 키가 없으면 보관하지 않는다`() {
        apple.registerAuthorizationCode("code", "r.apple")

        service.linkOnLogin(1L, null, CLIENT_ID)
        apple.isTokenApiConfigured = false
        service.linkOnLogin(1L, "code", CLIENT_ID)

        verify(exactly = 0) { userService.linkAppleRefreshToken(any(), any(), any()) }
    }

    @Test
    fun `코드 교환이 실패해도 예외를 밖으로 내지 않는다`() {
        assertThatCode { service.linkOnLogin(1L, "unknown-code", CLIENT_ID) }.doesNotThrowAnyException()
        verify(exactly = 0) { userService.linkAppleRefreshToken(any(), any(), any()) }
    }

    @Test
    fun `탈퇴하면 보관한 토큰을 복호화해 폐기한다`() {
        service.revokeOnWithdraw(UserWithdrawnEvent(1L, encryptor.encrypt("r.apple"), CLIENT_ID))

        assertThat(apple.revoked).containsExactly("r.apple" to CLIENT_ID)
    }

    @Test
    fun `Apple 토큰이 없는 회원은 폐기 요청을 보내지 않는다`() {
        service.revokeOnWithdraw(UserWithdrawnEvent(1L, null, null))

        assertThat(apple.revoked).isEmpty()
    }

    @Test
    fun `폐기가 실패해도 예외를 밖으로 내지 않는다`() {
        val failing = object : AppleOAuthClient by apple {
            override fun revokeRefreshToken(refreshToken: String, clientId: String) {
                throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)
            }
        }
        val failingService = AppleTokenService(failing, encryptor, userService)

        assertThatCode {
            failingService.revokeOnWithdraw(UserWithdrawnEvent(1L, encryptor.encrypt("r.apple"), CLIENT_ID))
        }.doesNotThrowAnyException()
    }

    companion object {
        private const val CLIENT_ID = "org.sesacteamproject.passmate.Passmate"
    }
}
