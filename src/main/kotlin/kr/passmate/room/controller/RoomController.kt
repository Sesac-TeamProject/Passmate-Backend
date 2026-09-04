package kr.passmate.room.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.dto.PageResponse
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.dto.PublicRoomResponse
import kr.passmate.room.dto.PublicRoomSearchRequest
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomResponse
import kr.passmate.room.dto.RoomSummaryResponse
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.service.PublicRoomQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.room.service.RoomService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "방 개설")
@RestController
@RequestMapping("/rooms")
class RoomController(
    private val roomService: RoomService,
    private val roomQueryService: RoomQueryService,
    private val publicRoomQueryService: PublicRoomQueryService,
) {

    @Operation(summary = "방 생성", description = "PIN 을 발급한다. 문제 세트는 나중에 연결해도 된다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: RoomCreateRequest,
    ): RoomResponse = RoomResponse.from(roomService.create(principal.userId, request))

    @Operation(
        summary = "공개 방 목록·검색 조회",
        description = "홈 인기 방 캐러셀과 탐색 화면. 호스트가 공개한 방과 브랜디드 방을 보여준다. " +
            "게스트도 조회할 수 있어 PIN 은 포함하지 않는다. 종료된 방은 나오지 않는다.",
    )
    @GetMapping("/public")
    fun publicRooms(
        @CurrentUser(required = false) principal: UserPrincipal?,
        @Valid @ModelAttribute request: PublicRoomSearchRequest,
    ): PageResponse<PublicRoomResponse> =
        PageResponse.from(publicRoomQueryService.search(request, principal?.userId)) { it }

    @Operation(summary = "방 상세 조회", description = "PIN 을 담고 있어 방에 속한 사람(호스트·참가자)만 볼 수 있다.")
    @GetMapping("/{roomId}")
    fun get(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
    ): RoomResponse = RoomResponse.from(roomQueryService.getRoomDetail(roomId, principal))

    @Operation(summary = "방 정보 수정", description = "대기 중일 때만 수정할 수 있다.")
    @PutMapping("/{roomId}")
    fun update(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: RoomUpdateRequest,
    ): RoomResponse = RoomResponse.from(roomService.update(roomId, principal.userId, request))

    @Operation(
        summary = "방 종료(취소)",
        description = "시작 전이면 CANCELED, 진행 중이었으면 ENDED. 어느 쪽이든 PIN 이 풀린다.",
    )
    @PostMapping("/{roomId}/close")
    fun close(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
    ): RoomResponse = RoomResponse.from(roomService.close(roomId, principal.userId))

    @Operation(summary = "방 QR 코드 조회", description = "입장 링크를 담은 PNG. 호스트만 받을 수 있다.")
    @GetMapping("/{roomId}/qr", produces = [MediaType.IMAGE_PNG_VALUE])
    fun qr(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
    ): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(roomQueryService.getQrPng(roomId, principal.userId))

    @Operation(
        summary = "PIN으로 방 조회",
        description = "입장 전 검증용. 인증 없이 부를 수 있다(게스트 입장 흐름).",
    )
    @GetMapping("/pin/{pin}")
    fun getByPin(@PathVariable pin: String): RoomSummaryResponse =
        RoomSummaryResponse.from(roomQueryService.getActiveRoomByPin(pin))
}
