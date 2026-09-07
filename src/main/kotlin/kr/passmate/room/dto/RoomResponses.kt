package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.domain.RoomType
import kr.passmate.room.service.JoinResult
import kr.passmate.room.service.NicknameCheckResult
import java.time.LocalDateTime

/**
 * 방 상세. 대기실(W-04)이 이 한 번의 응답으로 머리말(호스트 이름)과 KPI(문항 수·문항당 제한)를 채운다 —
 * 세트 목록·세트 상세를 따로 읽지 않게(웹 버그 리포트 B-21).
 */
@Schema(description = "방 상세")
data class RoomResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val topic: String?,
    val pin: String,
    val status: RoomStatus,
    val type: RoomType,
    val fee: Int?,
    val questionSetId: Long?,
    @field:Schema(description = "연결한 세트의 문항 수. 세트를 아직 연결하지 않았으면 null")
    val questionCount: Int?,
    @field:Schema(description = "연결한 세트의 예상 소요 시간(초, 문항 제한시간 합). 세트가 없으면 null")
    val estimatedSeconds: Int?,
    @field:Schema(description = "문항 제한시간의 최소(초). 전부 같으면 max 와 같다")
    val minTimeLimitSec: Int?,
    @field:Schema(description = "문항 제한시간의 최대(초)")
    val maxTimeLimitSec: Int?,
    val hostUserId: Long,
    @field:Schema(description = "호스트. 대기실 머리말의 선생님 이름 — 공개 방 카드와 같은 모양")
    val host: PublicRoomHostResponse,
    val maxParticipants: Int?,
    val participantCount: Int,
    val isPublic: Boolean,
    val screenLocked: Boolean,
    val currentQuestionNo: Int,
    val scheduledAt: LocalDateTime?,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
) {
    companion object {
        fun of(
            room: Room,
            hostNickname: String?,
            questionCount: Int?,
            estimatedSeconds: Int?,
            minTimeLimitSec: Int?,
            maxTimeLimitSec: Int?,
        ) = RoomResponse(
            id = room.id,
            title = room.title,
            description = room.description,
            topic = room.topic,
            pin = room.pin,
            status = room.status,
            type = room.type,
            fee = room.fee,
            questionSetId = room.questionSetId,
            questionCount = questionCount,
            estimatedSeconds = estimatedSeconds,
            minTimeLimitSec = minTimeLimitSec,
            maxTimeLimitSec = maxTimeLimitSec,
            hostUserId = room.hostUserId,
            host = PublicRoomHostResponse.of(room.hostUserId, hostNickname),
            maxParticipants = room.maxParticipants,
            participantCount = room.participantCount,
            isPublic = room.isPublic,
            screenLocked = room.screenLocked,
            currentQuestionNo = room.currentQuestionNo,
            scheduledAt = room.scheduledAt,
            startedAt = room.startedAt,
            endedAt = room.endedAt,
        )
    }
}

@Schema(description = "PIN 조회 결과 — 입장 화면에 필요한 최소 정보만 준다")
data class RoomSummaryResponse(
    val id: Long,
    val title: String,
    val topic: String?,
    val status: RoomStatus,
    val type: RoomType,
    val fee: Int?,
    val participantCount: Int,
    val maxParticipants: Int?,
    val guestAllowed: Boolean,
) {
    companion object {
        fun from(room: Room) = RoomSummaryResponse(
            id = room.id,
            title = room.title,
            topic = room.topic,
            status = room.status,
            type = room.type,
            fee = room.fee,
            participantCount = room.participantCount,
            maxParticipants = room.maxParticipants,
            guestAllowed = room.type == RoomType.FREE,
        )
    }
}

@Schema(description = "참가자")
data class ParticipantResponse(
    val id: Long,
    val nickname: String,
    val avatarId: String,
    val isGuest: Boolean,
    val joinedAt: LocalDateTime,
) {
    companion object {
        fun from(participant: Participant) = ParticipantResponse(
            id = participant.id,
            nickname = participant.nickname,
            avatarId = participant.avatarId,
            isGuest = participant.isGuest,
            joinedAt = participant.joinedAt,
        )
    }
}

@Schema(description = "입장 결과")
data class JoinRoomResponse(
    val participant: ParticipantResponse,
    @field:Schema(description = "게스트만 발급된다. 이 방의 API 를 부를 때 Bearer 로 쓴다")
    val accessToken: String?,
    @field:Schema(description = "게스트만 발급된다. 가입 후 기록을 계정에 옮길 때 제출한다(7일 보관)")
    val guestToken: String?,
) {
    companion object {
        fun from(result: JoinResult) = JoinRoomResponse(
            participant = ParticipantResponse.from(result.participant),
            accessToken = result.accessToken,
            guestToken = result.guestToken,
        )
    }
}

@Schema(description = "닉네임 중복 확인 결과")
data class NicknameCheckResponse(
    val available: Boolean,
    @field:Schema(description = "중복일 때 바로 쓸 수 있는 대안")
    val suggestions: List<String>,
) {
    companion object {
        fun from(result: NicknameCheckResult) =
            NicknameCheckResponse(result.available, result.suggestions)
    }
}
