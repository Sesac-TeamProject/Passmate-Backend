package kr.passmate.room.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.dto.HostedRoomsResponse
import kr.passmate.room.service.HostedRoomQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 마이페이지의 내 방 목록 (W-09 내가 만든 방). */
@Tag(name = "마이페이지")
@RestController
@RequestMapping("/users/me/rooms")
class MyRoomController(
    private val hostedRoomQueryService: HostedRoomQueryService,
) {

    @Operation(
        summary = "내가 만든 방 목록 조회",
        description = "진행 중(대기·진행)과 종료로 나눠서. 명성 요약(운영 횟수·누적 학생·평균 별점)을 함께 준다.",
    )
    @GetMapping("/hosted")
    fun hosted(@CurrentUser principal: UserPrincipal): HostedRoomsResponse =
        hostedRoomQueryService.getHostedRooms(principal.userId)
}
