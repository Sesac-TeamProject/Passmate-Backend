package kr.passmate.support

import kr.passmate.auth.client.AppleOAuthClient
import kr.passmate.auth.client.SocialAccount
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode

/**
 * 통합 테스트용 Apple 클라이언트. 실제 네트워크를 타지 않는다.
 * `registerIdToken`·`registerAuthorizationCode` 로 "이걸 내밀면 이 결과" 라는 대응을 미리 심어 둔다.
 * 폐기 요청은 `revoked` 에 쌓아 두고 테스트가 확인한다.
 */
class FakeAppleOAuthClient : AppleOAuthClient {

    private val accountsByToken = mutableMapOf<String, SocialAccount>()

    private val refreshTokensByCode = mutableMapOf<String, String>()

    val revoked = mutableListOf<Pair<String, String>>()

    override var isTokenApiConfigured: Boolean = true

    fun registerIdToken(idToken: String, account: SocialAccount) {
        accountsByToken[idToken] = account
    }

    fun registerAuthorizationCode(code: String, refreshToken: String) {
        refreshTokensByCode[code] = refreshToken
    }

    fun reset() {
        accountsByToken.clear()
        refreshTokensByCode.clear()
        revoked.clear()
        isTokenApiConfigured = true
    }

    override fun verifyIdToken(idToken: String): SocialAccount =
        accountsByToken[idToken] ?: throw BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID)

    override fun exchangeAuthorizationCode(code: String, clientId: String): String =
        refreshTokensByCode[code] ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)

    override fun revokeRefreshToken(refreshToken: String, clientId: String) {
        revoked += refreshToken to clientId
    }
}
