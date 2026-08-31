package kr.passmate.auth.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "passmate.google")
data class GoogleProperties(
    /** 웹·앱이 쓰는 OAuth 클라이언트 ID. ID 토큰의 aud 가 이 값과 같아야 한다. */
    val clientId: String,
    /** 인가 코드 교환에만 필요하다. ID 토큰 방식만 쓰면 비워도 된다. */
    val clientSecret: String,
    val redirectUri: String,
    val jwkSetUri: String,
    val tokenUri: String,
    /** Google 이 발급하는 iss 는 두 가지 표기를 오간다. */
    val issuers: List<String>,
)
