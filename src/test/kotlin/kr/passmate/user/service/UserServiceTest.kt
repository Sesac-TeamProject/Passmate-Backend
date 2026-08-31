package kr.passmate.user.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kr.passmate.coin.service.CoinWalletService
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.domain.User
import kr.passmate.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val coinWalletService = mockk<CoinWalletService>(relaxed = true)
    private val userService = UserService(userRepository, coinWalletService)

    @Test
    fun `미가입이면 자동 가입하고 코인 지갑을 함께 만든다`() {
        every { userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1") } returns null
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        val outcome = userService.loginOrRegister(
            provider = AuthProvider.GOOGLE,
            providerId = "google-1",
            email = "hyerim@example.com",
            name = "혜림",
            profileImageUrl = "https://img/1.png",
        )

        assertThat(outcome.isNewUser).isTrue()
        assertThat(outcome.user.nickname).isEqualTo("혜림")
        assertThat(outcome.user.lastLoginAt).isNotNull()
        // ERD coin_wallet — 첫 로그인 시 생성
        verify(exactly = 1) { coinWalletService.createFor(any()) }
    }

    @Test
    fun `이미 가입한 회원은 isNewUser 가 false 이고 지갑을 다시 만들지 않는다`() {
        val existing = activeUser(nickname = "기존닉네임")
        every { userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1") } returns existing

        val outcome = userService.loginOrRegister(
            provider = AuthProvider.GOOGLE,
            providerId = "google-1",
            email = "new@example.com",
            name = "구글에서바뀐이름",
            profileImageUrl = "https://img/2.png",
        )

        assertThat(outcome.isNewUser).isFalse()
        // 사용자가 직접 바꾼 닉네임을 소셜 프로필로 덮어쓰지 않는다
        assertThat(outcome.user.nickname).isEqualTo("기존닉네임")
        assertThat(outcome.user.email).isEqualTo("new@example.com")
        verify(exactly = 0) { coinWalletService.createFor(any()) }
    }

    @Test
    fun `닉네임이 없으면 이메일 아이디로 채운다`() {
        every { userRepository.findByProviderAndProviderId(any(), any()) } returns null
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        val outcome = userService.loginOrRegister(
            provider = AuthProvider.GOOGLE,
            providerId = "google-2",
            email = "passmate.dev@example.com",
            name = "   ",
            profileImageUrl = null,
        )

        assertThat(outcome.user.nickname).isEqualTo("passmate.dev")
    }

    @Test
    fun `닉네임이 컬럼 상한을 넘으면 잘라서 저장한다`() {
        every { userRepository.findByProviderAndProviderId(any(), any()) } returns null
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        val outcome = userService.loginOrRegister(
            provider = AuthProvider.GOOGLE,
            providerId = "google-3",
            email = null,
            name = "가".repeat(50),
            profileImageUrl = null,
        )

        assertThat(outcome.user.nickname).hasSize(User.NICKNAME_MAX_LENGTH)
    }

    @Test
    fun `제재된 계정은 로그인을 막는다`() {
        val suspended = activeUser().apply { suspend() }
        every { userRepository.findByProviderAndProviderId(any(), any()) } returns suspended

        assertThatThrownBy {
            userService.loginOrRegister(AuthProvider.GOOGLE, "google-1", null, null, null)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED)
    }

    @Test
    fun `탈퇴한 계정은 로그인을 막는다`() {
        val withdrawn = activeUser().apply { withdraw() }
        every { userRepository.findByProviderAndProviderId(any(), any()) } returns withdrawn

        assertThatThrownBy {
            userService.loginOrRegister(AuthProvider.GOOGLE, "google-1", null, null, null)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.USER_NOT_FOUND)
    }

    private fun activeUser(nickname: String = "닉네임") = User(
        provider = AuthProvider.GOOGLE,
        providerId = "google-1",
        email = "old@example.com",
        nickname = nickname,
        profileImageUrl = null,
    )
}
