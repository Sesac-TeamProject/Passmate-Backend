package kr.passmate.auth.client

/**
 * Apple 계정 검증. 구현체가 JWKS·HTTP 를 감추고, 실패는 전부 BusinessException 으로 번역한다.
 * 테스트는 Fake 구현으로 갈아끼운다.
 */
interface AppleOAuthClient {

    /** iOS 앱이 Sign in with Apple 로 받은 identity token(ID 토큰)을 검증한다. */
    fun verifyIdToken(idToken: String): SocialAccount
}
