package kr.passmate.hostlevel.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.hostlevel.dto.BadgeCollectionResponse
import kr.passmate.hostlevel.dto.HostGradeResponse
import kr.passmate.hostlevel.service.BadgeQueryService
import kr.passmate.hostlevel.service.HostGradeQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 호스트 등급 — W-09 내가 만든 방 상단 명성, M-09 명성·뱃지 상세.
 */
@Tag(name = "호스트 등급")
@RestController
@RequestMapping("/users/me")
class HostGradeController(
    private val hostGradeQueryService: HostGradeQueryService,
    private val badgeQueryService: BadgeQueryService,
) {

    @Operation(
        summary = "내 등급 조회",
        description = "현재 등급·집계값·다음 승급 조건별 진행도·유지 조건 현황·해금 기능. 기준은 서버가 계산해 내려준다.",
    )
    @GetMapping("/grade")
    fun myGrade(@CurrentUser principal: UserPrincipal): HostGradeResponse =
        hostGradeQueryService.myGrade(principal.userId)

    @Operation(
        summary = "내 뱃지 조회",
        description = "뱃지 컬렉션 8종. 못 딴 것도 진행도(예: 12/30)와 함께 내려간다.",
    )
    @GetMapping("/badges")
    fun myBadges(@CurrentUser principal: UserPrincipal): BadgeCollectionResponse =
        badgeQueryService.myBadges(principal.userId)
}
