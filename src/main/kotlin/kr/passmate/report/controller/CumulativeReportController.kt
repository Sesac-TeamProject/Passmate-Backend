package kr.passmate.report.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.report.dto.CumulativeReportResponse
import kr.passmate.report.service.CumulativeReportService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 마이페이지 상단 누적 요약 (W-13 · M-08).
 * 세션 하나짜리 리포트는 `/rooms/{id}/reports/me` 가 준다.
 */
@Tag(name = "결과·학습 리포트")
@RestController
@RequestMapping("/users/me")
class CumulativeReportController(
    private val cumulativeReportService: CumulativeReportService,
) {

    @Operation(
        summary = "누적 학습 리포트 조회",
        description = "참여한 전체 세션 누적 — 참여 횟수·평균 정답률·평균 순위·지난주 대비 변화·점수 추이·보완할 주제.",
    )
    @GetMapping("/report")
    fun cumulativeReport(@CurrentUser principal: UserPrincipal): CumulativeReportResponse =
        cumulativeReportService.getCumulativeReport(principal.userId)
}
