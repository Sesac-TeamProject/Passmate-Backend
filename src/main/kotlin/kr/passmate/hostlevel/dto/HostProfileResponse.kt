package kr.passmate.hostlevel.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.room.dto.PublicRoomResponse
import java.time.LocalDateTime

/**
 * 선생님 공개 프로필 (FR-066, M-10 프로필 바텀시트).
 *
 * 누구나 보는 화면이라 **공개해도 되는 값만** 담는다 — 이메일·코인·정산은 들어가지 않고,
 * 방 목록도 호스트가 공개로 설정한 것만 나간다.
 */
@Schema(description = "선생님 공개 프로필")
data class HostProfileResponse(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val defaultAvatarId: String?,
    @field:Schema(description = "활동 시작 시각 — 가입일")
    val activeSince: LocalDateTime?,
    val level: Int,
    val levelName: String,
    @field:Schema(description = "평균 별점. 받은 평가가 없으면 null")
    val avgRating: Double?,
    val ratingCount: Int,
    @field:Schema(description = "방 운영 횟수 — 시작해서 종료까지 간 방만 센다")
    val roomsHosted: Int,
    val totalStudents: Int,
    val badgeCount: Int,
    @field:Schema(description = "획득한 뱃지만. 못 딴 것은 남에게 보이지 않는다")
    val badges: List<BadgeResponse>,
    @field:Schema(description = "지금 열어 둔 공개 방(운영 중·예정). 비공개 방은 빠진다")
    val openRooms: List<PublicRoomResponse>,
)
