package kr.passmate.support

import kr.passmate.auth.client.GoogleAccount
import kr.passmate.auth.client.GoogleOAuthClient
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode

/**
 * 통합 테스트용 Google 클라이언트. 실제 네트워크를 타지 않는다.
 * `register` 로 "이 토큰을 내밀면 이 계정" 이라는 대응을 미리 심어 둔다.
 */
class FakeGoogleOAuthClient : GoogleOAuthClient {

    private val accountsByToken = mutableMapOf<String, GoogleAccount>()
    private val accountsByCode = mutableMapOf<String, GoogleAccount>()

    fun registerIdToken(idToken: String, account: GoogleAccount) {
        accountsByToken[idToken] = account
    }

    fun registerAuthorizationCode(code: String, account: GoogleAccount) {
        accountsByCode[code] = account
    }

    fun reset() {
        accountsByToken.clear()
        accountsByCode.clear()
    }

    override fun verifyIdToken(idToken: String): GoogleAccount =
        accountsByToken[idToken] ?: throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID)

    override fun exchangeAuthorizationCode(code: String, redirectUri: String?): GoogleAccount =
        accountsByCode[code] ?: throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID)
}
