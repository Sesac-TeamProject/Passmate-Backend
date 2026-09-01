package kr.passmate.report.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.report.domain.ParticipantReport
import java.time.LocalDateTime

/**
 * 개인 학습 리포트 (M-06). 세션이 끝날 때 찍어 둔 값을 그대로 읽는다.
 */
@Schema(description = "내 학습 리포트")
data class LearningReportResponse(
    val roomId: Long,
    val roomTitle: String,
    val participantId: Long,
    val nickname: String,
    val totalQuestions: Int,
    val correctCount: Int,
    @field:Schema(description = "정답률(%). 출제된 문항 수가 분모라 미제출도 못 맞힌 것으로 센다")
    val accuracy: Double,
    val totalScore: Int,
    val finalRank: Int,
    @field:Schema(description = "틀리거나 놓친 문항의 주제. 문항에 주제가 없으면 비어 있다")
    val weakTopics: List<String>,
    @field:Schema(description = "리포트 값에서 규칙으로 뽑은 조언. AI 가 쓰는 글이 아니다")
    val improvementPoints: List<String>,
    val generatedAt: LocalDateTime,
) {
    companion object {
        fun of(
            roomId: Long,
            roomTitle: String,
            nickname: String,
            report: ParticipantReport,
        ): LearningReportResponse {
            val weakTopics = report.weakTopics.orEmpty()
            return LearningReportResponse(
                roomId = roomId,
                roomTitle = roomTitle,
                participantId = report.participantId,
                nickname = nickname,
                totalQuestions = report.totalQuestions,
                correctCount = report.correctCount,
                accuracy = report.accuracy.toDouble(),
                totalScore = report.totalScore,
                finalRank = report.finalRank,
                weakTopics = weakTopics,
                improvementPoints = improvementPoints(report.accuracy.toDouble(), weakTopics),
                generatedAt = report.generatedAt,
            )
        }

        /**
         * 리포트 값만 보고 만드는 조언. **AI 를 부르지 않는다** —
         * 학생 수 × 세션 수만큼 호출이 늘어날 자리라 규칙으로 충분한 만큼만 쓴다.
         * 문구를 바꿀 일이 있으면 여기만 고치면 된다.
         */
        private fun improvementPoints(accuracy: Double, weakTopics: List<String>): List<String> = buildList {
            if (weakTopics.isNotEmpty()) {
                add("${weakTopics.joinToString(" · ")} 주제를 다시 살펴보세요.")
            }
            when {
                accuracy < LOW_ACCURACY ->
                    add("정답률이 ${accuracy}% 입니다. 틀린 문항의 해설부터 확인해 보세요.")

                accuracy >= HIGH_ACCURACY ->
                    add("정답률이 높습니다. 다음에는 더 어려운 방에 도전해 보세요.")
            }
            if (isEmpty()) add("고르게 잘 풀었습니다. 이 흐름을 유지해 보세요.")
        }

        /** 안내 문구를 가르는 기준선. 과금이나 권한과 무관한 표현용 값이다 */
        private const val LOW_ACCURACY = 60.0
        private const val HIGH_ACCURACY = 90.0
    }
}
