package kr.passmate.room.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.dto.EntryPaymentResponse
import kr.passmate.room.service.EntryPaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 참가비 결제 — W-11 유료 방 결제, M-01 v2 유료 방 입장.
 *
 * 게스트는 `UserPrincipal` 파라미터에서 이미 걸린다(유료 방은 회원 전용, FR-046).
 */
@Tag(name = "유료 방·결제")
@RestController
class EntryPaymentController(
    private val entryPaymentService: EntryPaymentService,
) {

    @Operation(
        summary = "참가비 코인 차감",
        description = "보유 코인에서 참가비를 차감하고 영수증 번호를 발급한다. " +
            "잔액이 모자라면 402 와 함께 부족 코인(data.shortfall)을 준다.",
    )
    @PostMapping("/rooms/{roomId}/entry-payments")
    @ResponseStatus(HttpStatus.CREATED)
    fun pay(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
    ): EntryPaymentResponse = entryPaymentService.pay(roomId, principal.userId)
}
