package kr.passmate.common.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/** 발급된 토큰 한 쌍. */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
)

/**
 * 액세스·리프레시 토큰 발급과 검증.
 *
 * Redis 후순위 결정에 따라 **stateless** 로 동작한다 — 서명과 만료만 확인하고
 * 서버에 토큰을 저장하지 않는다. 그래서 로그아웃은 즉시 무효화가 아니라 클라이언트의 토큰 폐기다.
 * Redis 도입 시 여기에 jti 블랙리스트 확인을 붙이면 나머지 코드는 그대로 둘 수 있다.
 */
@Component
class JwtTokenProvider(
    private val properties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray())

    fun issue(userId: Long, isAdmin: Boolean, now: Instant = Instant.now()): TokenPair =
        TokenPair(
            accessToken = build(userId, TokenType.ACCESS, isAdmin, now, properties.accessTokenValiditySeconds),
            refreshToken = build(userId, TokenType.REFRESH, isAdmin, now, properties.refreshTokenValiditySeconds),
            accessTokenExpiresIn = properties.accessTokenValiditySeconds,
        )

    /** 액세스 토큰을 검증하고 주체를 돌려준다. 만료·위조는 전부 401 로 번역한다. */
    fun parseAccessToken(token: String): UserPrincipal = parse(token, TokenType.ACCESS)

    /** 리프레시 토큰을 검증하고 주체를 돌려준다. */
    fun parseRefreshToken(token: String): UserPrincipal = parse(token, TokenType.REFRESH)

    private fun build(
        userId: Long,
        type: TokenType,
        isAdmin: Boolean,
        now: Instant,
        validitySeconds: Long,
    ): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_TYPE, type.name)
            .claim(CLAIM_ADMIN, isAdmin)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(validitySeconds)))
            .signWith(key)
            .compact()

    private fun parse(token: String, expected: TokenType): UserPrincipal {
        val claims = try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.TOKEN_EXPIRED, cause = e)
        } catch (e: JwtException) {
            throw BusinessException(ErrorCode.TOKEN_INVALID, cause = e)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.TOKEN_INVALID, cause = e)
        }

        if (claims[CLAIM_TYPE] != expected.name) {
            throw BusinessException(ErrorCode.TOKEN_INVALID, "${expected.name} 토큰이 아닙니다.")
        }
        val userId = claims.subject?.toLongOrNull()
            ?: throw BusinessException(ErrorCode.TOKEN_INVALID)
        return UserPrincipal(userId = userId, isAdmin = claims[CLAIM_ADMIN] as? Boolean ?: false)
    }

    companion object {
        private const val CLAIM_TYPE = "typ"
        private const val CLAIM_ADMIN = "adm"
    }
}
