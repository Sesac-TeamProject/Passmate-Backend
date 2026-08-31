package kr.passmate.auth.client

/**
 * Google 계정 검증. 구현체가 SDK·HTTP 를 감추고, 실패는 전부 BusinessException 으로 번역한다.
 * 테스트는 Fake 구현으로 갈아끼운다.
 */
interface GoogleOAuthClient {

    /** 클라이언트가 받은 ID 토큰을 검증한다(웹 GIS · 모바일 SDK). */
    fun verifyIdToken(idToken: String): GoogleAccount

    /** 인가 코드를 Google 과 교환해 ID 토큰을 받고 검증한다(웹 리다이렉트 플로우). */
    fun exchangeAuthorizationCode(code: String, redirectUri: String?): GoogleAccount
}
