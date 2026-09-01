package kr.passmate.report.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.report.dto.LearningReportResponse
import kr.passmate.report.service.ParticipantReportService
import kr.passmate.report.service.ReportExportService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 학습 리포트 — 개인 리포트(M-06)와 방 리포트 내보내기(W-07 "내보내기").
 */
@Tag(name = "결과·학습 리포트")
@RestController
@RequestMapping("/rooms/{roomId}/reports")
class LearningReportController(
    private val participantReportService: ParticipantReportService,
    private val reportExportService: ReportExportService,
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

    @Operation(
        summary = "리포트 내보내기",
        description = "세션 요약·문항별·학생별을 한 파일로. 지금은 csv 만 지원한다. 호스트만.",
    )
    @GetMapping("/export")
    fun export(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "csv") format: String,
    ): ResponseEntity<ByteArray> {
        val file = reportExportService.export(roomId, principal.userId, format)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, file.contentType)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName).build().toString(),
            )
            .body(file.content)
    }
}
