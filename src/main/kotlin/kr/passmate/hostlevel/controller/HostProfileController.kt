package kr.passmate.hostlevel.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.hostlevel.dto.HostProfileResponse
import kr.passmate.hostlevel.service.HostProfileQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 선생님 공개 프로필 — M-10 프로필 바텀시트, M-11 탐색에서 이름 탭.
 */
@Tag(name = "호스트 등급")
@RestController
@RequestMapping("/users/{userId}")
class HostProfileController(
    private val hostProfileQueryService: HostProfileQueryService,
) {

    @Operation(
        summary = "호스트 공개 프로필 조회",
        description = "닉네임·등급·획득 뱃지·평균 별점·운영 실적과 지금 열어 둔 공개 방. 공개 화면이라 로그인 없이도 볼 수 있다.",
    )
    @GetMapping("/profile")
    fun getProfile(@PathVariable userId: Long): HostProfileResponse =
        hostProfileQueryService.getProfile(userId)
}
