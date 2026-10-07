package kr.passmate.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Size

@Schema(description = "소셜 로그인 요청 — Google 은 idToken 또는 authorizationCode 중 하나, Apple 은 idToken(+authorizationCode)")
data class SocialLoginRequest(
    @field:Schema(description = "Google ID 토큰 (웹 GIS · 모바일 SDK) 또는 Apple identity token (iOS)")
    val idToken: String? = null,

    @field:Schema(description = "Google 인가 코드 (웹 리다이렉트 플로우) 또는 Apple 인가 코드 (iOS — 탈퇴 시 연결 해제용 refresh token 교환)")
    val authorizationCode: String? = null,

    @field:Schema(description = "인가 코드를 발급받을 때 쓴 redirect_uri. 생략하면 서버 설정값을 쓴다")
    val redirectUri: String? = null,

    @field:Schema(description = "Apple 전용 — 첫 인증 때만 앱이 받는 사용자 이름. 가입 시 닉네임 초기값으로만 쓴다")
    @field:Size(max = 60)
    val name: String? = null,
) {
    // 둘 다 오는 경우의 판단은 제공자마다 다르다(Google=거절, Apple=정상) — AuthService 가 한다
    @AssertTrue(message = "idToken 또는 authorizationCode 가 필요합니다.")
    fun isAnyCredential(): Boolean =
        !idToken.isNullOrBlank() || !authorizationCode.isNullOrBlank()
}
