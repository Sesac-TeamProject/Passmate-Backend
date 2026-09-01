package kr.passmate.user.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.user.dto.MyProfileResponse
import kr.passmate.user.service.UserProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "마이페이지")
@RestController
@RequestMapping("/users")
class UserController(
    private val userProfileService: UserProfileService,
) {

    @Operation(
        summary = "내 정보 조회",
        description = "프로필과 요약 지표(참여한 방·내가 만든 방·진행한 세션·누적 학생), 보유 코인. " +
            "등급·뱃지·평균 별점은 hostlevel·rating 기능을 붙일 때 이 응답에 더한다.",
    )
    @GetMapping("/me")
    fun me(@CurrentUser principal: UserPrincipal): MyProfileResponse =
        userProfileService.getMyProfile(principal.userId)
}
