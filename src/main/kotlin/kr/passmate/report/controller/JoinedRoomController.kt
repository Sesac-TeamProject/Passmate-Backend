package kr.passmate.report.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.report.dto.JoinedRoomsResponse
import kr.passmate.report.service.JoinedRoomQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 참여한 방 목록 (W-13 · M-08 · 홈 "최근 참여한 방").
 *
 * 같은 `/users/me/rooms` 아래지만 "내가 만든 방"은 room 기능이 맡는다 —
 * 이쪽은 알맹이가 점수·순위·리포트라 report 소유다.
 */
@Tag(name = "마이페이지")
@RestController
@RequestMapping("/users/me/rooms")
class JoinedRoomController(
    private val joinedRoomQueryService: JoinedRoomQueryService,
) {

    /**
     * `page`·`size` 를 직접 받는다. Spring 의 Pageable 을 쓰면 `sort` 파라미터를
     * 정렬 필드로 읽어 우리 정렬 규칙과 충돌한다(공개 방 목록에서 겪은 문제).
     */
    @Operation(
        summary = "참여한 방 목록 조회",
        description = "참가자로 들어갔던 방을 최근 순으로. 내 점수·순위·리포트 여부와 상단 요약을 함께 준다.",
    )
    @GetMapping("/joined")
    fun joinedRooms(
        @CurrentUser principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): JoinedRoomsResponse = joinedRoomQueryService.getJoinedRooms(principal.userId, page, size)
}
