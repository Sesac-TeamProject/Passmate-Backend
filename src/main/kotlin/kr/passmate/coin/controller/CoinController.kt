package kr.passmate.coin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.coin.dto.CoinBalanceResponse
import kr.passmate.coin.dto.CoinTransactionRow
import kr.passmate.coin.dto.PaymentMethodRequest
import kr.passmate.coin.dto.PaymentMethodResponse
import kr.passmate.coin.service.CoinQueryService
import kr.passmate.coin.service.CoinWalletService
import kr.passmate.common.dto.PageResponse
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내 코인 — C-02 v3 · M-12 코인 카드, C-02-9 코인 내역.
 */
@Tag(name = "유료 방·결제")
@RestController
class CoinController(
    private val coinQueryService: CoinQueryService,
    private val coinWalletService: CoinWalletService,
) {

    @Operation(
        summary = "내 코인 조회",
        description = "보유 코인(1 C = ₩1)·기본 결제 수단·최근 내역 1건. 유료 방 입장 시 부족분 계산에 쓴다.",
    )
    @GetMapping("/users/me/coins")
    fun myCoins(@CurrentUser principal: UserPrincipal): CoinBalanceResponse =
        coinQueryService.myCoins(principal.userId)

    /** `page`·`size` 를 직접 받는다 — Pageable 을 쓰면 `sort` 가 우리 정렬 규칙과 충돌한다. */
    @Operation(
        summary = "코인 내역 조회",
        description = "충전·차감·환급 내역을 최근 순 페이징으로. 건별 잔액이 함께 나온다.",
    )
    @GetMapping("/users/me/coins/transactions")
    fun myTransactions(
        @CurrentUser principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<CoinTransactionRow> = coinQueryService.myTransactions(principal.userId, page, size)

    @Operation(
        summary = "기본 결제 수단 설정",
        description = "코인 충전 화면이 미리 골라 둘 수단. 카드 정보는 저장하지 않는다(포트원이 처리).",
    )
    @PutMapping("/users/me/payment-method")
    fun changePaymentMethod(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: PaymentMethodRequest,
    ): PaymentMethodResponse =
        // @NotNull 로 이미 걸러졌다. 여기 오면 값이 있다
        coinWalletService.changePaymentMethod(principal.userId, request.method!!)
}
