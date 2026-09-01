package kr.passmate.report.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.report.dto.LearningReportResponse
import kr.passmate.report.service.ParticipantReportService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 학습 리포트 — 개인 리포트(M-06).
 */
@Tag(name = "결과·학습 리포트")
@RestController
@RequestMapping("/rooms/{roomId}/reports")
class LearningReportController(
    private val participantReportService: ParticipantReportService,
) {

    @Operation(
        summary = "내 학습 리포트 조회",
        description = "세션 종료 시 만들어진 개인 리포트(정답률·취약 주제·개선 포인트). 게스트도 자기 것은 본다.",
    )
    @GetMapping("/me")
    fun myReport(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
    ): LearningReportResponse = participantReportService.myReport(roomId, principal)
}
