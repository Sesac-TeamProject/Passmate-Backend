package kr.passmate.report.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.common.dto.PageResponse
import kr.passmate.room.domain.RoomStatus
import java.time.LocalDateTime

/** 참여한 방 한 줄 (W-13 · M-08 · 홈 "최근 참여한 방"). */
@Schema(description = "참여한 방")
data class JoinedRoom(
    val roomId: Long,
    val title: String,
    val hostNickname: String,
    val status: RoomStatus,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val questionCount: Int,
    @field:Schema(description = "유료 방 참가비(코인). 무료 방은 null")
    val fee: Int?,
    @field:Schema(description = "내 최종 점수. 세션이 끝나야 나온다")
    val myScore: Int?,
    val myRank: Int?,
    val myAccuracy: Double?,
    @field:Schema(description = "학습 리포트를 볼 수 있는지. 세션이 끝난 방만 true")
    val hasReport: Boolean,
)

/** 참여한 방 목록 상단 요약 (W-13). 누적 리포트와 같은 값을 쓴다. */
@Schema(description = "참여 요약")
data class JoinedSummary(
    val completedSessionCount: Int,
    val averageAccuracy: Double,
    val averageRank: Double,
    val weakTopics: List<String>,
)

@Schema(description = "참여한 방 목록")
data class JoinedRoomsResponse(
    val summary: JoinedSummary,
    val rooms: PageResponse<JoinedRoom>,
)
