package kr.passmate.report.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 점수 추이 한 점 (W-13 상단 그래프). */
@Schema(description = "세션별 성적")
data class SessionTrendPoint(
    val roomId: Long,
    val roomTitle: String,
    val totalScore: Int,
    val accuracy: Double,
    val finalRank: Int,
    val playedAt: LocalDateTime,
)

/**
 * 참여한 전체 세션 누적 지표 (FR-033, W-13 · M-08 상단 요약).
 *
 * 세션이 끝날 때 찍어 둔 개인 리포트만 모은다 — 진행 중인 방은 아직 성적이 없다.
 */
@Schema(description = "누적 학습 리포트")
data class CumulativeReportResponse(
    @field:Schema(description = "참여한 방 수. 진행 중인 방도 센다")
    val joinedRoomCount: Int,
    @field:Schema(description = "성적이 나온 세션 수. 평균들의 분모다")
    val completedSessionCount: Int,
    val averageAccuracy: Double,
    val averageRank: Double,
    @field:Schema(description = "최근 7일 평균 정답률 - 그 앞 7일 평균. 비교할 기록이 없으면 null")
    val accuracyChangeFromLastWeek: Double?,
    @field:Schema(description = "최근 세션부터 차례로. 그래프용")
    val trend: List<SessionTrendPoint>,
    @field:Schema(description = "자주 틀린 주제 (많이 나온 순)")
    val weakTopics: List<String>,
)
