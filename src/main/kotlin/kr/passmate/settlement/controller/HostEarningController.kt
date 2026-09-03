package kr.passmate.settlement.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.settlement.dto.HostEarningsResponse
import kr.passmate.settlement.service.HostEarningQueryService
import kr.passmate.settlement.service.SettlementExportService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 수익·정산 내역 — W-10 정산, M-T4.
 */
@Tag(name = "정산")
@RestController
@RequestMapping("/users/me/earnings")
class HostEarningController(
    private val hostEarningQueryService: HostEarningQueryService,
    private val settlementExportService: SettlementExportService,
) {

    @Operation(
        summary = "내 수익·정산 내역 조회",
        description = "이번 달 수익·지급 예정액·다음 지급일·세션별 내역(참가비·수수료 20%·정산액 80%·상태).",
    )
    @GetMapping
    fun myEarnings(@CurrentUser principal: UserPrincipal): HostEarningsResponse =
        hostEarningQueryService.myEarnings(principal.userId)

    @Operation(summary = "정산 내역 내보내기", description = "세션별 적립을 CSV 로 내려받는다.")
    @GetMapping("/export")
    fun export(
        @CurrentUser principal: UserPrincipal,
        @RequestParam(defaultValue = "csv") format: String,
    ): ResponseEntity<ByteArray> {
        val file = settlementExportService.export(principal.userId, format)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, file.contentType)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.fileName).build().toString(),
            )
            .body(file.content)
    }
}
