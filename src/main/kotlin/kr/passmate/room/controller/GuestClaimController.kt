package kr.passmate.room.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.dto.GuestClaimRequest
import kr.passmate.room.dto.GuestClaimResponse
import kr.passmate.room.service.GuestClaimService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 게스트 기록 전환 — M-05 최종 결과 화면의 "가입하고 기록 저장하기".
 *
 * 가입(= Google 로그인) **직후 회원 토큰으로** 부른다. 게스트 토큰으로는 부를 수 없다 —
 * 기록을 받을 계정이 있어야 옮길 곳이 정해진다.
 */
@Tag(name = "게스트 기록 전환")
@RestController
@RequestMapping("/guest-records")
class GuestClaimController(
    private val guestClaimService: GuestClaimService,
) {

    @Operation(
        summary = "게스트 기록 계정 연동",
        description = "입장 시 받은 게스트 토큰을 제출하면 그 세션 기록이 계정에 붙는다. 보관 기한이 지났으면 409.",
    )
    @PostMapping("/claim")
    fun claim(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: GuestClaimRequest,
    ): GuestClaimResponse =
        guestClaimService.claim(principal.userId, request)
}
