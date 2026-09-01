package kr.passmate.feedback.dto

import kr.passmate.feedback.domain.AiFeedback
import kr.passmate.feedback.domain.AiFeedbackStatus
import kr.passmate.question.domain.QuestionType
import java.time.LocalDateTime

/** 화면이 분기하는 네 가지 상태. 아직 요청하지 않은 것과 진행 중인 것은 다르다. */
enum class AnalysisStatus {
    NOT_REQUESTED,
    PENDING,
    DONE,
    FAILED,
    ;

    companion object {
        fun of(feedback: AiFeedback?): AnalysisStatus = when (feedback?.status) {
            null -> NOT_REQUESTED
            AiFeedbackStatus.PENDING -> PENDING
            AiFeedbackStatus.DONE -> DONE
            AiFeedbackStatus.FAILED -> FAILED
        }
    }
}

/** 분석 본문. DONE 일 때만 채워진다. */
data class EssayAnalysisView(
    val keyPoints: List<String>,
    val missingPoints: List<String>,
    val suggestions: List<String>,
    val summary: String,
    val completedAt: LocalDateTime?,
) {
    companion object {
        fun from(feedback: AiFeedback): EssayAnalysisView? {
            if (feedback.status != AiFeedbackStatus.DONE) return null
            return EssayAnalysisView(
                keyPoints = feedback.keyPoints.orEmpty(),
                missingPoints = feedback.missingPoints.orEmpty(),
                suggestions = feedback.suggestions.orEmpty(),
                summary = feedback.summary.orEmpty(),
                completedAt = feedback.completedAt,
            )
        }
    }
}

/**
 * 내 답안 + AI 피드백. 분석이 실패해도 정오·점수는 그대로 볼 수 있다 —
 * 실패 사유는 로그에만 남기고 화면에는 상태(FAILED)만 알린다.
 */
data class MyAnswerResponse(
    val roomId: Long,
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    val type: QuestionType,
    val content: String,
    val points: Int,
    val submitted: String,
    val isCorrect: Boolean?,
    val score: Int,
    val finalScore: Int,
    val submittedAt: LocalDateTime,
    /** 문항이 마감된 뒤에만 채운다 — 진행 중에 내보내면 정답이 샌다 */
    val answer: String?,
    val explanation: String?,
    val analysisStatus: AnalysisStatus,
    val analysis: EssayAnalysisView?,
    /** 이번 달 남은 무료 분석 횟수. 게스트는 분석 자체가 막혀 있어 null */
    val remainingFreeAnalysis: Int?,
    /** 무료 한도를 넘겼을 때 1건당 차감할 코인 */
    val analysisCoinCost: Int,
)

/** 분석 요청 응답. 즉시 PENDING 으로 돌아가고, 완료는 조회 API 로 확인한다. */
data class EssayAnalysisRequestResponse(
    val analysisStatus: AnalysisStatus,
    /** 이번 요청에 실제로 차감된 코인. 0 이면 무료 한도로 처리됐다는 뜻 */
    val chargedCoins: Int,
    val remainingFreeAnalysis: Int,
    val analysisCoinCost: Int,
)
