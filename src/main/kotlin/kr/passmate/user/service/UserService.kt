package kr.passmate.user.service

import kr.passmate.coin.service.CoinWalletService
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.domain.User
import kr.passmate.user.domain.UserStatus
import kr.passmate.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 소셜 로그인 결과 — 신규 가입인지까지 함께 알려준다(응답의 isNewUser). */
data class LoginOutcome(
    val user: User,
    val isNewUser: Boolean,
)

@Service
class UserService(
    private val userRepository: UserRepository,
    private val coinWalletService: CoinWalletService,
) {

    /**
     * 소셜 계정으로 로그인한다. 미가입이면 그 자리에서 가입시킨다(FR-001).
     * 외부 API 호출은 이 트랜잭션에 들어오지 않는다 — 호출자가 검증을 끝내고 결과만 넘긴다.
     */
    @Transactional
    fun loginOrRegister(
        provider: AuthProvider,
        providerId: String,
        email: String?,
        name: String?,
        profileImageUrl: String?,
    ): LoginOutcome {
        val existing = userRepository.findByProviderAndProviderId(provider, providerId)
        val outcome = existing
            ?.also { it.syncSocialProfile(email, profileImageUrl) }
            ?.let { LoginOutcome(it, isNewUser = false) }
            ?: LoginOutcome(register(provider, providerId, email, name, profileImageUrl), isNewUser = true)

        // 제재 계정은 로그인 자체를 막는다. 해제되면 즉시 다시 로그인된다(FR-063)
        verifyLoginable(outcome.user)
        outcome.user.recordLogin()
        return outcome
    }

    /** 마이페이지에서 고친 프로필을 반영한다. 탈퇴·정지 계정은 getActiveUser 가 막는다. */
    @Transactional
    fun updateProfile(userId: Long, nickname: String, profileImageUrl: String?, defaultAvatarId: String?): User =
        getActiveUser(userId).apply { updateProfile(nickname, profileImageUrl, defaultAvatarId) }

    @Transactional(readOnly = true)
    fun getActiveUser(userId: Long): User {
        val user = userRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
        verifyLoginable(user)
        return user
    }

    private fun register(
        provider: AuthProvider,
        providerId: String,
        email: String?,
        name: String?,
        profileImageUrl: String?,
    ): User {
        val user = userRepository.save(
            User(
                provider = provider,
                providerId = providerId,
                email = email,
                nickname = resolveNickname(name, email),
                profileImageUrl = profileImageUrl,
            ),
        )
        // 코인 지갑은 첫 로그인 시 만든다(ERD coin_wallet 주석). 다른 기능의 Service 를 통해서만 만든다
        coinWalletService.createFor(user.id)
        return user
    }

    /** 소셜 프로필 이름 → 이메일 아이디 → 고정 문구 순으로 닉네임을 정한다. 컬럼 상한은 30자. */
    private fun resolveNickname(name: String?, email: String?): String {
        val candidate = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_NICKNAME
        return candidate.take(User.NICKNAME_MAX_LENGTH)
    }

    private fun verifyLoginable(user: User) {
        when (user.status) {
            UserStatus.ACTIVE -> Unit
            // TODO(moderation): 제재 사유를 sanction 에서 읽어 함께 내려준다. 지금은 코드만 구분한다
            UserStatus.SUSPENDED -> throw BusinessException(ErrorCode.ACCOUNT_SUSPENDED)
            UserStatus.DELETED -> throw BusinessException(ErrorCode.USER_NOT_FOUND, "탈퇴한 계정입니다.")
        }
    }

    companion object {
        private const val DEFAULT_NICKNAME = "패스메이트 회원"
    }
}
