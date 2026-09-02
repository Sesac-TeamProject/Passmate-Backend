package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.room.domain.RoomStatus
import java.time.LocalDateTime

/**
 * 호스트 명성 요약 (W-09 · M-T3 상단).
 *
 * 등급·다음 레벨 진행률은 hostlevel 기능이 붙기 전이라 null 이다 —
 * 0 으로 내보내면 "Lv.0" 으로 읽혀 없는 등급을 만들어 낸다.
 */
@Schema(description = "호스트 명성 요약")
data class HostReputation(
    @field:Schema(description = "호스트 등급. 아직 판정된 적 없으면 null")
    val level: Int?,
    @field:Schema(description = "다음 등급까지 진행률(0~1). 최고 등급이거나 미판정이면 null")
    val nextLevelProgress: Double?,
    @field:Schema(description = "방 운영 횟수 — 시작해서 종료까지 간 방만 센다")
    val hostedSessionCount: Long,
    val totalStudentCount: Long,
    @field:Schema(description = "평균 별점. 받은 평가가 없으면 null")
    val averageStars: Double?,
    val ratingCount: Int,
)

@Schema(description = "진행 중이거나 시작 전인 내 방")
data class ActiveHostedRoom(
    val roomId: Long,
    val title: String,
    @field:Schema(description = "입장 PIN. 내 방이라 호스트에게는 보여 준다")
    val pin: String,
    val status: RoomStatus,
    val scheduledAt: LocalDateTime?,
    val startedAt: LocalDateTime?,
    val participantCount: Int,
    val currentQuestionNo: Int,
)

@Schema(description = "끝난 내 방")
data class EndedHostedRoom(
    val roomId: Long,
    val title: String,
    val endedAt: LocalDateTime?,
    @field:Schema(description = "그 세션에 들어왔던 학생 수. 중간에 나간 사람도 센다")
    val studentCount: Long,
    @field:Schema(description = "평균 정답률(%). 세션 종료 때 계산해 둔 값")
    val correctRate: Double?,
    val averageStars: Double?,
    val ratingCount: Int,
)

@Schema(description = "내가 만든 방 목록")
data class HostedRoomsResponse(
    val reputation: HostReputation,
    @field:Schema(description = "대기·진행 중 — 바로 이어서 진행할 방")
    val active: List<ActiveHostedRoom>,
    val ended: List<EndedHostedRoom>,
)
