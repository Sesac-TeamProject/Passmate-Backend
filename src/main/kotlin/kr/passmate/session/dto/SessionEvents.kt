package kr.passmate.session.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.question.domain.QuestionType
import kr.passmate.session.domain.SessionEventType
import java.time.LocalDateTime

/** WebSocket 으로 나가는 모든 이벤트의 겉봉투. 클라이언트는 type 으로 분기한다. */
data class SessionEvent<T>(
    val type: SessionEventType,
    val roomId: Long,
    val occurredAt: LocalDateTime,
    val payload: T?,
) {
    companion object {
        fun <T> of(type: SessionEventType, roomId: Long, payload: T? = null) =
            SessionEvent(type, roomId, LocalDateTime.now(), payload)
    }
}

/**
 * 문항 시작 알림.
 *
 * ⚠️ **정답(answer)·해설(explanation)은 절대 넣지 않는다.** 이 페이로드는 브라우저
 * 개발자도구에 그대로 보인다. 정답은 QUESTION_ENDED 에서 처음 나간다.
 */
@Schema(description = "문항 시작 — 정답 미포함")
data class QuestionStartedPayload(
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    val totalCount: Int,
    val type: QuestionType,
    val content: String,
    val choices: List<String>?,
    val points: Int,
    val timeLimitSec: Int,
    @field:Schema(description = "서버가 발급한 마감 시각. 클라이언트는 이 값으로 남은 시간을 표시만 한다")
    val endsAt: LocalDateTime,
)

@Schema(description = "문항 마감 — 여기서 처음 정답이 나간다")
data class QuestionEndedPayload(
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    val answer: String?,
    val explanation: String?,
    val submitCount: Int,
    val correctCount: Int,
    val correctRate: Double,
    @field:Schema(description = "보기별 응답 수. 서술형은 비어 있다")
    val distribution: Map<String, Int>,
)

@Schema(description = "랭킹 한 줄")
data class RankingEntry(
    val rank: Int,
    val participantId: Long,
    val nickname: String,
    val avatarId: String,
    val totalScore: Long,
)

@Schema(description = "제출 현황 — 호스트 전용")
data class SubmissionStatusPayload(
    val sessionQuestionId: Long,
    val submitCount: Int,
    val participantCount: Int,
    val correctCount: Int,
    val correctRate: Double,
    val distribution: Map<String, Int>,
)
