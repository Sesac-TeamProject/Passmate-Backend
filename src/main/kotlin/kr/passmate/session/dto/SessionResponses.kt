package kr.passmate.session.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.room.domain.RoomStatus
import kr.passmate.session.domain.Answer
import java.time.LocalDateTime

@Schema(description = "재접속 복구용 세션 스냅샷")
data class SessionSnapshotResponse(
    val roomId: Long,
    val status: RoomStatus,
    val currentQuestionNo: Int,
    val totalCount: Int,
    val screenLocked: Boolean,
    @field:Schema(description = "지금 열려 있는 문항. 없으면 null. 정답은 들어 있지 않다")
    val currentQuestion: QuestionStartedPayload?,
    @field:Schema(description = "현재 문항에 내가 이미 제출했는지")
    val submitted: Boolean,
    val ranking: List<RankingEntry>,
)

@Schema(description = "답안 제출 결과")
data class AnswerResponse(
    val answerId: Long,
    val sessionQuestionId: Long,
    @field:Schema(description = "서술형은 채점 전이라 null")
    val isCorrect: Boolean?,
    val baseScore: Int,
    val speedBonus: Int,
    val score: Int,
    val submittedAt: LocalDateTime,
) {
    companion object {
        fun from(answer: Answer) = AnswerResponse(
            answerId = answer.id,
            sessionQuestionId = answer.sessionQuestionId,
            isCorrect = answer.isCorrect,
            baseScore = answer.baseScore,
            speedBonus = answer.speedBonus,
            score = answer.score,
            submittedAt = answer.submittedAt,
        )
    }
}

@Schema(description = "문항 결과 — 마감된 문항만 조회된다")
data class QuestionResultResponse(
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    val answer: String?,
    val explanation: String?,
    val submitCount: Int,
    val correctCount: Int,
    val correctRate: Double,
    val distribution: Map<String, Int>,
    val ranking: List<RankingEntry>,
)

@Schema(description = "답안 제출 요청")
data class AnswerSubmitRequest(
    @field:jakarta.validation.constraints.NotBlank(message = "답안은 비어 있을 수 없습니다.")
    val submitted: String,
)
