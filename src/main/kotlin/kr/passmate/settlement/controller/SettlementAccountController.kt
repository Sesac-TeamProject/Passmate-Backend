package kr.passmate.settlement.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.settlement.dto.SettlementAccountRequest
import kr.passmate.settlement.dto.SettlementAccountResponse
import kr.passmate.settlement.service.SettlementAccountService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 정산 계좌 — W-10 정산, M-T4.
 */
@Tag(name = "정산")
@RestController
@RequestMapping("/users/me/settlement-account")
class SettlementAccountController(
    private val settlementAccountService: SettlementAccountService,
) {

    @Operation(
        summary = "정산 계좌 조회",
        description = "은행·마스킹 계좌번호·예금주·검증 상태. 등록한 적 없으면 registered=false.",
    )
    @GetMapping
    fun myAccount(@CurrentUser principal: UserPrincipal): SettlementAccountResponse =
        settlementAccountService.myAccount(principal.userId)

    @Operation(
        summary = "정산 계좌 등록/변경",
        description = "회원당 하나라 덮어쓴다. 계좌를 바꾸면 실명 확인 상태는 초기화된다. 미등록이면 정산이 보류된다.",
    )
    @PutMapping
    fun upsert(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: SettlementAccountRequest,
    ): SettlementAccountResponse =
        settlementAccountService.upsert(principal.userId, request)
}
