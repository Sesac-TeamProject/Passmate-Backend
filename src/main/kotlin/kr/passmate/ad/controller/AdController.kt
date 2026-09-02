package kr.passmate.ad.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.ad.domain.AdPlacement
import kr.passmate.ad.dto.AdEventRequest
import kr.passmate.ad.dto.AdListResponse
import kr.passmate.ad.service.AdEventService
import kr.passmate.ad.service.AdQueryService
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 광고 — 결과 화면 하단·대기실 배너·리포트 하단·홈 카드.
 */
@Tag(name = "광고")
@RestController
@RequestMapping("/ads")
class AdController(
    private val adQueryService: AdQueryService,
    private val adEventService: AdEventService,
) {

    @Operation(
        summary = "광고 조회",
        description = "그 자리에 지금 집행 중인 광고 소재. 게스트도 봐야 하는 화면이라 로그인 없이 열린다.",
    )
    @GetMapping
    fun ads(@RequestParam placement: AdPlacement): AdListResponse =
        adQueryService.adsAt(placement)

    @Operation(
        summary = "광고 노출/클릭 집계",
        description = "노출(IMPRESSION)·클릭(CLICK)을 기록해 캠페인 노출수·클릭률에 반영한다. " +
            "게스트의 노출도 받는다 — 빼면 광고주 수치가 실제보다 낮아진다.",
    )
    @PostMapping("/{adId}/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun recordEvent(
        @CurrentUser(required = false) principal: AuthPrincipal?,
        @PathVariable adId: Long,
        @Valid @RequestBody request: AdEventRequest,
    ) = adEventService.record(adId, request.type, principal)
}
