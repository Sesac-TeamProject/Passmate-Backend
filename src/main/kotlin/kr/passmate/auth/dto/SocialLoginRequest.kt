package kr.passmate.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue

@Schema(description = "소셜 로그인 요청 — idToken 또는 authorizationCode 중 하나만 보낸다")
data class SocialLoginRequest(
    @field:Schema(description = "Google ID 토큰 (웹 GIS · 모바일 SDK)")
    val idToken: String? = null,

    @field:Schema(description = "Google 인가 코드 (웹 리다이렉트 플로우)")
    val authorizationCode: String? = null,

    @field:Schema(description = "인가 코드를 발급받을 때 쓴 redirect_uri. 생략하면 서버 설정값을 쓴다")
    val redirectUri: String? = null,
) {
    @AssertTrue(message = "idToken 또는 authorizationCode 중 하나만 보내야 합니다.")
    fun isExactlyOneCredential(): Boolean =
        idToken.isNullOrBlank() != authorizationCode.isNullOrBlank()
}
