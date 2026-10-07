package kr.passmate.auth.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "passmate.apple")
data class AppleProperties(
    /** 네이티브 로그인 토큰의 aud 는 앱 번들 ID 다. 빌드마다 번들 ID 가 다르면 쉼표로 여러 개를 둔다. */
    val clientIds: List<String>,
    val jwkSetUri: String,
    val issuer: String,
    val tokenUri: String,
    val revokeUri: String,
    /** Apple Developer 팀 ID — client secret JWT 의 iss. 공개값이다 */
    val teamId: String,
    /** Sign in with Apple 키(.p8)의 Key ID — client secret JWT 헤더의 kid. 공개값이다 */
    val keyId: String,
    /**
     * .p8 파일 내용(PKCS#8 EC 개인키). **비밀** — 운영은 SSM 으로만 주입한다.
     * .env 는 한 줄 값만 받으므로 BEGIN/END 줄·줄바꿈(또는 `\n` 글자)이 있어도 없어도 읽는다.
     */
    val privateKey: String,
) {
    /** 인가 코드 교환·토큰 폐기(탈퇴)에 필요한 값이 다 있는지. 없으면 로그인만 되고 폐기는 건너뛴다 */
    val isTokenApiConfigured: Boolean
        get() = teamId.isNotBlank() && keyId.isNotBlank() && privateKey.isNotBlank()
}
