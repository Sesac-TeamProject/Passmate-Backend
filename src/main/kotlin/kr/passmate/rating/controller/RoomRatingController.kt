package kr.passmate.rating.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.rating.dto.RoomRatingListResponse
import kr.passmate.rating.dto.RoomRatingRequest
import kr.passmate.rating.dto.RoomRatingResponse
import kr.passmate.rating.service.RoomRatingQueryService
import kr.passmate.rating.service.RoomRatingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 별점·평가 — M-06 v2 선생님 별점 시트(제출), W-09·M-13 종료된 방 상세(조회).
 */
@Tag(name = "별점·평가")
@RestController
@RequestMapping("/rooms/{roomId}/ratings")
class RoomRatingController(
    private val roomRatingService: RoomRatingService,
    private val roomRatingQueryService: RoomRatingQueryService,
) {

    @Operation(
        summary = "세션 평가 제출",
        description = "별점(1~5)+태그+한 줄 후기. 답안을 낸 참가자만, 종료 후 정해진 시간 안에, 1회. 게스트도 가능하다.",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: RoomRatingRequest,
    ): RoomRatingResponse =
        RoomRatingResponse.from(roomRatingService.submit(roomId, principal, request))

    @Operation(
        summary = "방 평가 목록 조회",
        description = "별점·태그·한 줄 후기 목록과 평균·별점 분포·태그 집계. 호스트 본인만. 누가 남겼는지는 나가지 않는다.",
    )
    @GetMapping
    fun list(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
    ): RoomRatingListResponse =
        roomRatingQueryService.listOfRoom(roomId, principal.userId)
}
