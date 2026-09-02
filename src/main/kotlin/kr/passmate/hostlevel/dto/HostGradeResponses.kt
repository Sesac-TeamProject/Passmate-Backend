package kr.passmate.hostlevel.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 승급 조건 한 줄의 진행도. 화면이 "24/40" 처럼 그대로 그린다. */
@Schema(description = "승급 조건별 진행도")
data class GradeRequirement(
    @field:Schema(description = "ROOMS_HOSTED · TOTAL_STUDENTS · AVG_RATING")
    val type: String,
    val label: String,
    val current: Double,
    val target: Double,
    val met: Boolean,
)

@Schema(description = "Lv.4~5 유지 조건 충족 현황")
data class GradeMaintenance(
    @field:Schema(description = "유지 판정 기간(일)")
    val windowDays: Long,
    val sessionsInWindow: Int,
    val requiredSessions: Int,
    val avgRating: Double?,
    val requiredAvgRating: Double?,
    val met: Boolean,
    val nextEvaluationAt: LocalDateTime?,
)

/**
 * 내 명성 (FR-045~048, W-09 · M-09).
 *
 * 화면이 등급 기준을 따로 들고 있지 않도록 **조건과 진행도를 서버가 계산해서** 내려준다.
 * 기준이 바뀌어도 앱을 새로 내지 않아도 된다.
 */
@Schema(description = "내 등급·명성")
data class HostGradeResponse(
    val level: Int,
    val levelName: String,
    val levelAchievedAt: LocalDateTime?,
    @field:Schema(description = "방 운영 횟수 — 시작해서 종료까지 간 방만 센다")
    val roomsHosted: Int,
    val totalStudents: Int,
    @field:Schema(description = "평균 별점. 받은 평가가 없으면 null")
    val avgRating: Double?,
    val ratingCount: Int,
    @field:Schema(description = "다음 등급. 최고 등급이면 null")
    val nextLevel: Int?,
    val nextLevelName: String?,
    @field:Schema(description = "다음 등급 조건별 진행도. 최고 등급이면 비어 있다")
    val nextRequirements: List<GradeRequirement>,
    @field:Schema(description = "다음 등급까지 종합 진행률(0~1)")
    val nextLevelProgress: Double?,
    @field:Schema(description = "평가 표본이 모자라 승급이 보류된 상태인지(FR-046)")
    val ratingSamplePending: Boolean,
    @field:Schema(description = "Lv.1~3 은 유지 조건이 없어 null")
    val maintenance: GradeMaintenance?,
    @field:Schema(description = "지금 등급까지 열린 기능")
    val unlocked: List<String>,
    val lastEvaluatedAt: LocalDateTime?,
)

@Schema(description = "등급 판정 배치 실행 결과")
data class GradeEvaluationResult(
    val evaluated: Int,
    val promoted: Int,
    val demoted: Int,
)
