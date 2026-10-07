package kr.passmate.support

import kr.passmate.auth.client.AppleOAuthClient
import kr.passmate.auth.client.SocialAccount
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode

/**
 * 통합 테스트용 Apple 클라이언트. 실제 네트워크를 타지 않는다.
 * `registerIdToken` 으로 "이 토큰을 내밀면 이 계정" 이라는 대응을 미리 심어 둔다.
 */
class FakeAppleOAuthClient : AppleOAuthClient {

    private val accountsByToken = mutableMapOf<String, SocialAccount>()

    fun registerIdToken(idToken: String, account: SocialAccount) {
        accountsByToken[idToken] = account
    }

    fun reset() {
        accountsByToken.clear()
    }

    override fun verifyIdToken(idToken: String): SocialAccount =
        accountsByToken[idToken] ?: throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID)
}
