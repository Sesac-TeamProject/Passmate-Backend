package kr.passmate.feedback.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

/**
 * 첨삭 등록·수정(upsert, FR-038). 답안당 한 장이라 POST 가 아니라 PUT 이다.
 *
 * 세 항목 모두 선택이고, **넘어온 값 그대로** 저장한다 —
 * null 을 "안 바꿈"으로 다루면 한 번 단 코멘트를 지울 수 없어진다.
 */
@Schema(description = "선생님 첨삭 등록·수정")
data class TeacherReviewRequest(
    @field:Schema(description = "학생에게 보일 코멘트")
    @field:Size(max = COMMENT_MAX)
    val comment: String? = null,

    @field:Schema(description = "보정 점수. 0~문항 배점. 서술형만 보정할 수 있다. null 이면 점수는 그대로 둔다")
    @field:Min(0)
    val adjustedScore: Int? = null,

    @field:Schema(description = "개선사항")
    @field:Size(max = COMMENT_MAX)
    val improvement: String? = null,
) {
    companion object {
        /** teacher_review.comment·improvement 는 TEXT 지만, 첨삭 한 장이 소설이 될 이유는 없다 */
        const val COMMENT_MAX = 2000
    }
}
