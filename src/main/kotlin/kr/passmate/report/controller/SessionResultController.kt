package kr.passmate.report.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.report.dto.MySessionResultResponse
import kr.passmate.report.dto.ParticipantResultResponse
import kr.passmate.report.dto.SessionResultsResponse
import kr.passmate.report.service.SessionResultQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 세션이 끝난 뒤의 결과 화면 — 방 리포트(W-07 · M-14)와 내 결과(M-05).
 */
@Tag(name = "결과·학습 리포트")
@RestController
@RequestMapping("/rooms/{roomId}/results")
class SessionResultController(
    private val sessionResultQueryService: SessionResultQueryService,
) {

    @Operation(
        summary = "세션 전체 통계 조회",
        description = "요약·문항별 정답률·학생별 점수와 순위. 학생별 점수가 통째로 나가므로 호스트만.",
    )
    @GetMapping
    fun sessionResults(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
    ): SessionResultsResponse = sessionResultQueryService.sessionResults(roomId, principal.userId)

    @Operation(
        summary = "내 세션 결과 조회",
        description = "최종 점수·순위와 문항별 정오·AI 피드백·첨삭, 평가 가능 여부. 게스트도 자기 것은 본다.",
    )
    @GetMapping("/me")
    fun myResult(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
    ): MySessionResultResponse = sessionResultQueryService.myResult(roomId, principal)

    @Operation(
        summary = "학생별 결과 상세 조회",
        description = "특정 학생의 문항별 답변·정오·점수·AI 피드백·첨삭. 남의 답안이라 호스트만.",
    )
    @GetMapping("/participants/{participantId}")
    fun participantResult(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @PathVariable participantId: Long,
    ): ParticipantResultResponse =
        sessionResultQueryService.participantResult(roomId, participantId, principal.userId)
}
