package kr.passmate.moderation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.moderation.dto.ReportRequest
import kr.passmate.moderation.dto.ReportResponse
import kr.passmate.moderation.service.ReportService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 신고 접수 — M-10 프로필 시트, 세션 화면의 신고 버튼.
 */
@Tag(name = "신고·제재")
@RestController
@RequestMapping("/reports")
class ReportController(
    private val reportService: ReportService,
) {

    @Operation(
        summary = "신고 접수",
        description = "참가자·호스트·문제·방을 유형과 사유로 신고한다. 게스트도 낼 수 있고, 접수되면 미처리(OPEN)로 등록된다.",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun report(
        @CurrentUser principal: AuthPrincipal,
        @Valid @RequestBody request: ReportRequest,
    ): ReportResponse =
        ReportResponse.from(reportService.report(principal, request))
}
