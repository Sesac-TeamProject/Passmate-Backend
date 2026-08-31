package kr.passmate.common.security

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class JwtTokenProviderTest {

    private val properties = JwtProperties(
        secret = "test-secret-key-for-unit-tests-at-least-32-bytes-long",
        accessTokenValiditySeconds = 3600,
        refreshTokenValiditySeconds = 1209600,
    )
    private val provider = JwtTokenProvider(properties)

    @Test
    fun `액세스 토큰에서 주체와 관리자 여부를 되읽는다`() {
        val tokens = provider.issue(userId = 42L, isAdmin = true)

        val principal = provider.parseAccessToken(tokens.accessToken)

        assertThat(principal.userId).isEqualTo(42L)
        assertThat(principal.isAdmin).isTrue()
        assertThat(tokens.accessTokenExpiresIn).isEqualTo(3600)
    }

    @Test
    fun `리프레시 토큰을 액세스 토큰 자리에 쓰면 거부한다`() {
        val tokens = provider.issue(userId = 1L, isAdmin = false)

        assertThatThrownBy { provider.parseAccessToken(tokens.refreshToken) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.TOKEN_INVALID)
    }

    @Test
    fun `액세스 토큰으로는 재발급받을 수 없다`() {
        val tokens = provider.issue(userId = 1L, isAdmin = false)

        assertThatThrownBy { provider.parseRefreshToken(tokens.accessToken) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.TOKEN_INVALID)
    }

    @Test
    fun `만료된 토큰은 TOKEN_EXPIRED 로 구분한다`() {
        // 만료가 401 로 나가야 클라이언트의 refresh 재시도가 발화한다
        val expired = provider.issue(userId = 1L, isAdmin = false, now = Instant.now().minusSeconds(7200))

        assertThatThrownBy { provider.parseAccessToken(expired.accessToken) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.TOKEN_EXPIRED)
    }

    @Test
    fun `다른 키로 서명된 토큰은 거부한다`() {
        val forged = JwtTokenProvider(properties.copy(secret = "another-secret-key-that-is-also-32-bytes-long!"))
            .issue(userId = 1L, isAdmin = true)

        assertThatThrownBy { provider.parseAccessToken(forged.accessToken) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.TOKEN_INVALID)
    }

    @Test
    fun `형식이 아닌 문자열은 거부한다`() {
        assertThatThrownBy { provider.parseAccessToken("not-a-jwt") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.TOKEN_INVALID)
    }
}
