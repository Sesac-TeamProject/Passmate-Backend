package kr.passmate.auth.client

/** Google 이 확인해 준 계정 정보. 서비스 계층은 이 형태만 안다. */
data class GoogleAccount(
    /** Google 계정 고유 식별자(sub). user.provider_id 에 저장한다. */
    val providerId: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
    val pictureUrl: String?,
)
