package kr.passmate.common.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "passmate.jwt")
data class JwtProperties(
    /** HS256 서명 키. 32바이트 이상이어야 한다. 운영은 SSM Parameter Store 에서 주입한다. */
    val secret: String,
    val accessTokenValiditySeconds: Long,
    val refreshTokenValiditySeconds: Long,
) {
    val accessTokenValidity: Duration get() = Duration.ofSeconds(accessTokenValiditySeconds)
    val refreshTokenValidity: Duration get() = Duration.ofSeconds(refreshTokenValiditySeconds)
}
