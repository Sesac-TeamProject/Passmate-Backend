package kr.passmate.auth.dto

import kr.passmate.common.security.TokenPair

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
) {
    companion object {
        fun of(tokens: TokenPair) = TokenResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn = tokens.accessTokenExpiresIn,
        )
    }
}
