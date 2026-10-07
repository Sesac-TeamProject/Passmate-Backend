package kr.passmate.auth.client

/**
 * Apple 계정 검증과 토큰 API. 구현체가 JWKS·HTTP 를 감추고, 실패는 전부 BusinessException 으로 번역한다.
 * 테스트는 Fake 구현으로 갈아끼운다.
 */
interface AppleOAuthClient {

    /** 인가 코드 교환·토큰 폐기에 필요한 키(.p8)가 설정됐는지. */
    val isTokenApiConfigured: Boolean

    /**
     * iOS 앱이 Sign in with Apple 로 받은 identity token(ID 토큰)을 검증한다.
     * 돌려주는 계정의 clientId 는 토큰의 aud(앱 번들 ID)다 — 코드 교환·폐기를 같은 클라이언트로 해야 한다.
     */
    fun verifyIdToken(idToken: String): SocialAccount

    /** 앱이 함께 보낸 authorization code 를 Apple 과 교환해 refresh token 을 받는다. 탈퇴 때 폐기하려고 보관한다 */
    fun exchangeAuthorizationCode(code: String, clientId: String): String

    /** 탈퇴한 회원의 refresh token 을 폐기해 앱과 Apple ID 연결을 끊는다(App Store 심사 지침 5.1.1(v)). */
    fun revokeRefreshToken(refreshToken: String, clientId: String)
}
