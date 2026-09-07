package kr.passmate.feedback.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.feedback.dto.QuestionCommentRequest
import kr.passmate.feedback.dto.QuestionCommentResponse
import kr.passmate.feedback.service.QuestionCommentService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 문항 단위 선생님 코멘트 — 방 리포트 문항별 탭(W-07)의 우측 패널.
 * 저장된 값은 방 리포트의 문항 줄과 학생 결과(M-06)의 문항 줄에 함께 실린다.
 */
@Tag(name = "선생님 첨삭")
@RestController
@RequestMapping("/rooms/{roomId}/questions")
class QuestionCommentController(
    private val questionCommentService: QuestionCommentService,
) {

    @Operation(
        summary = "문항 코멘트 저장",
        description = "학생 전체에게 남기는 문항 단위 첨삭. 문항당 한 장이라 다시 저장하면 덮어쓴다(upsert). " +
            "답안별 첨삭(PUT …/answers/{answerId}/review)과 별개다. 호스트만.",
    )
    @PutMapping("/{questionId}/comment")
    fun upsertComment(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @PathVariable questionId: Long,
        @Valid @RequestBody request: QuestionCommentRequest,
    ): QuestionCommentResponse =
        questionCommentService.upsert(roomId, questionId, principal.userId, request)
}
