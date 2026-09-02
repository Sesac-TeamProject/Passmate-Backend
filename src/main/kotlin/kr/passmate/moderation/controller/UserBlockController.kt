package kr.passmate.moderation.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.moderation.dto.BlockedUsersResponse
import kr.passmate.moderation.service.BlockedUserQueryService
import kr.passmate.moderation.service.UserBlockService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 차단 — M-10 프로필 시트, C-02 v3 마이페이지 설정.
 */
@Tag(name = "신고·제재")
@RestController
@RequestMapping("/users")
class UserBlockController(
    private val userBlockService: UserBlockService,
    private val blockedUserQueryService: BlockedUserQueryService,
) {

    @Operation(
        summary = "사용자 차단",
        description = "차단한 호스트의 방은 인기·탐색·공개 목록에서 빠지고 프로필이 막힌다. PIN 직접 입장은 그대로 된다.",
    )
    @PostMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun block(@CurrentUser principal: UserPrincipal, @PathVariable userId: Long) =
        userBlockService.block(principal.userId, userId)

    @Operation(summary = "사용자 차단 해제", description = "차단한 적 없어도 조용히 끝난다.")
    @DeleteMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(@CurrentUser principal: UserPrincipal, @PathVariable userId: Long) =
        userBlockService.unblock(principal.userId, userId)

    @Operation(summary = "차단 목록 조회", description = "내가 차단한 호스트(닉네임·등급·차단일). 해제 진입점.")
    @GetMapping("/me/blocks")
    fun myBlocks(@CurrentUser principal: UserPrincipal): BlockedUsersResponse =
        blockedUserQueryService.myBlocks(principal.userId)
}
