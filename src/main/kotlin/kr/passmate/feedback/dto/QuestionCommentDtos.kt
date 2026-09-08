package kr.passmate.feedback.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kr.passmate.feedback.domain.SessionQuestionComment
import java.time.LocalDateTime

@Schema(description = "문항 단위 선생님 코멘트 저장 요청 — 학생 전체 대상 (W-07)")
data class QuestionCommentRequest(
    @field:Schema(description = "코멘트 본문. 다시 저장하면 덮어쓴다")
    @field:NotBlank(message = "코멘트는 비어 있을 수 없습니다.")
    @field:Size(max = MAX_LENGTH, message = "코멘트는 ${MAX_LENGTH}자를 넘을 수 없습니다.")
    val comment: String,
) {
    companion object {
        const val MAX_LENGTH = 2000
    }
}

@Schema(description = "문항 단위 선생님 코멘트")
data class QuestionCommentResponse(
    val roomId: Long,
    val questionId: Long,
    val comment: String,
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(roomId: Long, questionId: Long, comment: SessionQuestionComment) = QuestionCommentResponse(
            roomId = roomId,
            questionId = questionId,
            comment = comment.comment,
            updatedAt = comment.updatedAt ?: comment.createdAt,
        )
    }
}
