package kr.passmate.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kr.passmate.auth.dto.LoginResponse
import kr.passmate.auth.service.DevAuthService
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class DevLoginRequest(
    @field:NotBlank(message = "key 는 필수입니다.")
    @field:Size(max = 50)
    val key: String,
    val nickname: String? = null,
    val email: String? = null,
)

/**
 * 개발용 로그인. **local · dev 프로파일에서만 등록된다.**
 * 같은 key 로 부르면 같은 계정이 나오므로 웹·앱 팀이 고정 계정으로 붙여볼 수 있다.
 */
@Profile("local", "dev")
@Tag(name = "회원가입·로그인")
@RestController
@RequestMapping("/auth")
class DevAuthController(
    private val devAuthService: DevAuthService,
) {

    @Operation(
        summary = "[개발 전용] 로그인",
        description = "Google 검증 없이 key 로 계정을 만들거나 찾아 토큰을 발급한다. 운영 프로파일에는 없는 API 다.",
    )
    @PostMapping("/dev-login")
    fun devLogin(@Valid @RequestBody request: DevLoginRequest): LoginResponse =
        devAuthService.login(request.key, request.nickname, request.email)
}
