package kr.passmate.common.security

import io.jsonwebtoken.Claims
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
 * 액세스·리프레시·게스트 토큰 발급과 검증.
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
            accessToken = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TokenType.ACCESS.name)
                .claim(CLAIM_ADMIN, isAdmin)
                .expiration(now, properties.accessTokenValiditySeconds)
                .issuedAt(Date.from(now))
                .signWith(key)
                .compact(),
            refreshToken = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TokenType.REFRESH.name)
                .claim(CLAIM_ADMIN, isAdmin)
                .expiration(now, properties.refreshTokenValiditySeconds)
                .issuedAt(Date.from(now))
                .signWith(key)
                .compact(),
            accessTokenExpiresIn = properties.accessTokenValiditySeconds,
        )

    /**
     * 게스트 참가자 토큰. subject 는 participant.id 이고 roomId 를 함께 담아
     * **입장한 방 하나에만** 쓸 수 있게 한다. 유효 기간은 액세스 토큰과 같다.
     */
    fun issueGuestToken(participantId: Long, roomId: Long, now: Instant = Instant.now()): String =
        Jwts.builder()
            .subject(participantId.toString())
            .claim(CLAIM_TYPE, TokenType.GUEST.name)
            .claim(CLAIM_ROOM, roomId)
            .expiration(now, properties.accessTokenValiditySeconds)
            .issuedAt(Date.from(now))
            .signWith(key)
            .compact()

    /** 액세스 토큰(회원)만 통과시킨다. */
    fun parseAccessToken(token: String): UserPrincipal =
        parse(token).let { claims ->
            claims.requireType(TokenType.ACCESS)
            UserPrincipal(claims.subjectAsLong(), claims[CLAIM_ADMIN] as? Boolean ?: false)
        }

    fun parseRefreshToken(token: String): UserPrincipal =
        parse(token).let { claims ->
            claims.requireType(TokenType.REFRESH)
            UserPrincipal(claims.subjectAsLong(), claims[CLAIM_ADMIN] as? Boolean ?: false)
        }

    /** 회원·게스트 어느 쪽이든 받는다. 필터가 SecurityContext 를 채울 때 쓴다. */
    fun parseAuthToken(token: String): AuthPrincipal {
        val claims = parse(token)
        return when (claims[CLAIM_TYPE]) {
            TokenType.ACCESS.name ->
                UserPrincipal(claims.subjectAsLong(), claims[CLAIM_ADMIN] as? Boolean ?: false)

            TokenType.GUEST.name ->
                GuestPrincipal(
                    participantId = claims.subjectAsLong(),
                    roomId = (claims[CLAIM_ROOM] as? Number)?.toLong()
                        ?: throw BusinessException(ErrorCode.TOKEN_INVALID),
                )

            // 리프레시 토큰으로는 API 를 부를 수 없다
            else -> throw BusinessException(ErrorCode.TOKEN_INVALID)
        }
    }

    private fun parse(token: String): Claims =
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.TOKEN_EXPIRED, cause = e)
        } catch (e: JwtException) {
            throw BusinessException(ErrorCode.TOKEN_INVALID, cause = e)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.TOKEN_INVALID, cause = e)
        }

    private fun Claims.requireType(expected: TokenType) {
        if (this[CLAIM_TYPE] != expected.name) {
            throw BusinessException(ErrorCode.TOKEN_INVALID, "${expected.name} 토큰이 아닙니다.")
        }
    }

    private fun Claims.subjectAsLong(): Long =
        subject?.toLongOrNull() ?: throw BusinessException(ErrorCode.TOKEN_INVALID)

    private fun io.jsonwebtoken.JwtBuilder.expiration(now: Instant, validitySeconds: Long) =
        expiration(Date.from(now.plusSeconds(validitySeconds)))

    companion object {
        private const val CLAIM_TYPE = "typ"
        private const val CLAIM_ADMIN = "adm"
        private const val CLAIM_ROOM = "rid"
    }
}
