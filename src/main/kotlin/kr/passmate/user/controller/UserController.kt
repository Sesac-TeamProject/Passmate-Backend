package kr.passmate.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.user.dto.MyProfileResponse
import kr.passmate.user.dto.UserProfileUpdateRequest
import kr.passmate.user.service.UserProfileService
import kr.passmate.user.service.UserWithdrawService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "마이페이지")
@RestController
@RequestMapping("/users")
class UserController(
    private val userProfileService: UserProfileService,
    private val userWithdrawService: UserWithdrawService,
) {

    @Operation(
        summary = "내 정보 조회",
        description = "프로필과 요약 지표(참여한 방·내가 만든 방·진행한 세션·누적 학생), 보유 코인. " +
            "등급·뱃지·평균 별점은 hostlevel·rating 기능을 붙일 때 이 응답에 더한다.",
    )
    @GetMapping("/me")
    fun me(@CurrentUser principal: UserPrincipal): MyProfileResponse =
        userProfileService.getMyProfile(principal.userId)

    @Operation(
        summary = "내 정보 수정",
        description = "닉네임·프로필 이미지·기본 캐릭터를 고친다. 바뀐 프로필을 그대로 돌려준다.",
    )
    @PutMapping("/me")
    fun updateMe(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: UserProfileUpdateRequest,
    ): MyProfileResponse = userProfileService.updateMyProfile(principal.userId, request)

    @Operation(
        summary = "회원 탈퇴",
        description = "계정을 내리고 개인정보를 지운다. 보유 코인은 소멸하고, 발급된 토큰은 더 이상 통하지 않는다. " +
            "진행 중이거나 시작 전인 방이 있으면 409 로 막는다.",
    )
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@CurrentUser principal: UserPrincipal) = userWithdrawService.withdraw(principal.userId)
}
