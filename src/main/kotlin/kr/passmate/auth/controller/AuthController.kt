package kr.passmate.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.auth.dto.LoginResponse
import kr.passmate.auth.dto.SocialLoginRequest
import kr.passmate.auth.dto.TokenRefreshRequest
import kr.passmate.auth.dto.TokenResponse
import kr.passmate.auth.service.AuthService
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.user.domain.AuthProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "회원가입·로그인")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {

    @Operation(
        summary = "구글 로그인(회원가입 겸용)",
        description = "미가입이면 자동 가입하고 isNewUser 와 함께 토큰을 발급한다. provider 허용값은 google 뿐이다.",
    )
    @PostMapping("/login/{provider}")
    fun login(
        @PathVariable provider: String,
        @Valid @RequestBody request: SocialLoginRequest,
    ): LoginResponse = authService.login(AuthProvider.from(provider), request)

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스 토큰을 다시 받는다.")
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: TokenRefreshRequest,
    ): TokenResponse = authService.refresh(request)

    @Operation(
        summary = "로그아웃",
        description = "클라이언트가 토큰을 폐기하는 것이 로그아웃이다. 토큰은 stateless 라 서버가 즉시 무효화하지 않는다.",
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@CurrentUser principal: UserPrincipal) {
        authService.logout(principal.userId)
    }
}
