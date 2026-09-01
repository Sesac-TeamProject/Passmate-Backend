package kr.passmate.feedback.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.question.domain.QuestionType
import java.time.LocalDateTime

/**
 * 첨삭 대상 답안 한 건 (W-07 문항별 우측 패널 "준영의 답변 1/6").
 *
 * 호스트가 출제자라 모범답안을 함께 준다 — 첨삭하려면 기준이 옆에 있어야 한다.
 */
@Schema(description = "첨삭 대상 답안")
data class ReviewTargetAnswer(
    val answerId: Long,
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    val type: QuestionType,
    val questionContent: String,
    val points: Int,
    @field:Schema(description = "모범답안. 첨삭 기준이라 호스트에게는 항상 준다")
    val modelAnswer: String?,
    val participantId: Long,
    val nickname: String,
    val avatarId: String,
    val submitted: String,
    val isCorrect: Boolean?,
    val score: Int,
    val finalScore: Int,
    val submittedAt: LocalDateTime,
    val analysisStatus: AnalysisStatus,
    val analysis: EssayAnalysisView?,
    @field:Schema(description = "이미 첨삭했는지. 목록에서 남은 것을 골라내는 데 쓴다")
    val reviewed: Boolean,
    val teacherReview: TeacherReviewView?,
)

@Schema(description = "첨삭 대상 답안 목록")
data class ReviewTargetListResponse(
    val roomId: Long,
    val totalCount: Int,
    @field:Schema(description = "그중 첨삭이 끝난 수. \"3/6 첨삭 완료\" 를 그리는 값")
    val reviewedCount: Int,
    val answers: List<ReviewTargetAnswer>,
)
