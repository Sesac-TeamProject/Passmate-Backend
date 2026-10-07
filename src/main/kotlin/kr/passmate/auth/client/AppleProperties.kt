package kr.passmate.auth.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "passmate.apple")
data class AppleProperties(
    /** 네이티브 로그인 토큰의 aud 는 앱 번들 ID 다. 빌드마다 번들 ID 가 다르면 쉼표로 여러 개를 둔다. */
    val clientIds: List<String>,
    val jwkSetUri: String,
    val issuer: String,
)
