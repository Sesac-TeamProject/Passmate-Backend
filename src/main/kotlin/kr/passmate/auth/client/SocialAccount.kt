package kr.passmate.auth.client

/** 소셜 제공자(Google·Apple)가 확인해 준 계정 정보. 서비스 계층은 이 형태만 안다. */
data class SocialAccount(
    /** 제공자 계정 고유 식별자(sub). user.provider_id 에 저장한다. */
    val providerId: String,
    val email: String?,
    val emailVerified: Boolean,
    /** Apple 은 ID 토큰에 이름을 싣지 않는다 — 앱이 첫 인증 때만 받아 요청에 실어 보낸다. */
    val name: String?,
    val pictureUrl: String?,
    /** 토큰이 발급된 클라이언트 ID(aud). Apple 코드 교환·토큰 폐기에만 쓴다 */
    val clientId: String? = null,
)
