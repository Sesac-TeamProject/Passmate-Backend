package kr.passmate.feedback.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.feedback.domain.TeacherReview

/**
 * 첨삭 등록·수정 결과. 첨삭 자체와 **그 결과 최종 점수가 얼마가 됐는지**를 함께 준다 —
 * 호스트 패널이 저장 직후 점수 칸을 다시 조회하지 않아도 되게.
 */
@Schema(description = "첨삭 등록·수정 결과")
data class TeacherReviewResponse(
    val answerId: Long,
    val participantId: Long,
    @field:Schema(description = "보정이 반영된 최종 점수. 보정을 지우면 채점기가 낸 잠정 점수로 돌아간다")
    val finalScore: Int,
    val review: TeacherReviewView,
) {
    companion object {
        fun of(review: TeacherReview, participantId: Long, finalScore: Int) = TeacherReviewResponse(
            answerId = review.answerId,
            participantId = participantId,
            finalScore = finalScore,
            review = TeacherReviewView.from(review),
        )
    }
}
