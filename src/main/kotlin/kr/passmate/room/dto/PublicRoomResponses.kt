package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.domain.RoomType
import java.time.LocalDateTime

/**
 * 홈 인기 방 캐러셀·탐색 카드(W-01 v6 · M-01 v6 · M-11).
 *
 * **PIN 은 넣지 않는다** — 목록은 게스트도 볼 수 있어서, PIN 을 실으면
 * 공개 목록이 곧 모든 방의 입장 코드 목록이 된다.
 */
@Schema(description = "공개 방 카드")
data class PublicRoomResponse(
    val id: Long,
    val title: String,
    @field:Schema(description = "주제 태그")
    val topic: String?,
    val status: RoomStatus,
    val type: RoomType,
    @field:Schema(description = "참가비(코인). 무료 방은 null")
    val fee: Int?,
    @field:Schema(description = "문항 수. 세트를 아직 연결하지 않았으면 null")
    val questionCount: Int?,
    @field:Schema(description = "참여 중 인원")
    val participantCount: Int,
    val maxParticipants: Int?,
    val host: PublicRoomHostResponse,
    @field:Schema(description = "예정 시작 시각")
    val scheduledAt: LocalDateTime?,
    val startedAt: LocalDateTime?,
) {
    companion object {
        fun of(room: Room, hostNickname: String?, questionCount: Int?) = PublicRoomResponse(
            id = room.id,
            title = room.title,
            topic = room.topic,
            status = room.status,
            type = room.type,
            fee = room.fee,
            questionCount = questionCount,
            participantCount = room.participantCount,
            maxParticipants = room.maxParticipants,
            host = PublicRoomHostResponse(room.hostUserId, hostNickname ?: UNKNOWN_HOST),
            scheduledAt = room.scheduledAt,
            startedAt = room.startedAt,
        )

        private const val UNKNOWN_HOST = "알 수 없음"
    }
}

/**
 * 방 카드의 호스트. 등급은 hostlevel 기능을 붙일 때 더한다 —
 * 지금 0 이나 null 로 내보내면 "Lv.0" 처럼 읽혀 없는 정보를 있는 것처럼 만든다.
 */
@Schema(description = "방 카드의 호스트")
data class PublicRoomHostResponse(
    val userId: Long,
    val nickname: String,
)
