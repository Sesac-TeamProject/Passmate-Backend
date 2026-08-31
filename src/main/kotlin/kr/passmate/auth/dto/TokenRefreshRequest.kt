package kr.passmate.auth.dto

import jakarta.validation.constraints.NotBlank

data class TokenRefreshRequest(
    @field:NotBlank(message = "refreshToken 은 필수입니다.")
    val refreshToken: String,
)
