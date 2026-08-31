package kr.passmate.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.common.security.TokenPair
import kr.passmate.user.service.LoginOutcome

@Schema(description = "로그인 응답")
data class LoginResponse(
    @field:Schema(description = "이번 요청으로 새로 가입했는지 — true 면 온보딩을 띄운다")
    val isNewUser: Boolean,
    val accessToken: String,
    val refreshToken: String,
    @field:Schema(description = "액세스 토큰 만료까지 남은 초")
    val expiresIn: Long,
    val user: UserSummary,
) {
    companion object {
        fun of(outcome: LoginOutcome, tokens: TokenPair) = LoginResponse(
            isNewUser = outcome.isNewUser,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn = tokens.accessTokenExpiresIn,
            user = UserSummary.from(outcome.user),
        )
    }
}
