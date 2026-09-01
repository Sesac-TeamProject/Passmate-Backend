package kr.passmate.feedback.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.feedback.dto.ReviewTargetListResponse
import kr.passmate.feedback.service.TeacherReviewQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 선생님 첨삭 — W-07 방 리포트의 문항별 우측 패널.
 */
@Tag(name = "선생님 첨삭")
@RestController
@RequestMapping("/rooms/{roomId}/answers")
class TeacherReviewController(
    private val teacherReviewQueryService: TeacherReviewQueryService,
) {

    @Operation(
        summary = "첨삭 대상 답안 목록 조회",
        description = "답안 본문·정오·점수·AI 피드백·첨삭 여부. 문항·학생으로 좁힐 수 있다. 호스트만.",
    )
    @GetMapping
    fun listReviewTargets(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @RequestParam(required = false) questionId: Long?,
        @RequestParam(required = false) participantId: Long?,
    ): ReviewTargetListResponse =
        teacherReviewQueryService.listReviewTargets(roomId, principal.userId, questionId, participantId)
}
