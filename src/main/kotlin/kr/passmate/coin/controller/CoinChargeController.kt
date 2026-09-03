package kr.passmate.coin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.coin.dto.CoinChargeConfirmResponse
import kr.passmate.coin.dto.CoinChargeRequest
import kr.passmate.coin.dto.CoinChargeResponse
import kr.passmate.coin.service.CoinChargeService
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 코인 충전 — C-02-4 충전 금액·수단, W-11 결제창.
 */
@Tag(name = "유료 방·결제")
@RestController
@RequestMapping("/coins/charges")
class CoinChargeController(
    private val coinChargeService: CoinChargeService,
) {

    @Operation(
        summary = "코인 충전 요청",
        description = "충전 건을 만들고 결제창 호출 파라미터를 준다. 이 단계에서는 코인이 늘지 않는다(READY).",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun request(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: CoinChargeRequest,
    ): CoinChargeResponse = coinChargeService.request(principal.userId, request)

    @Operation(
        summary = "코인 충전 승인 검증",
        description = "포트원에 실제 상태·금액을 물어 대조한 뒤 코인을 적립한다. " +
            "roomId 를 함께 요청했다면 참가비 차감까지 이어서 처리한다. 웹훅과 멱등.",
    )
    @PostMapping("/{chargeId}/confirm")
    fun confirm(
        @CurrentUser principal: UserPrincipal,
        @PathVariable chargeId: Long,
    ): CoinChargeConfirmResponse = coinChargeService.confirm(chargeId, principal.userId)
}
