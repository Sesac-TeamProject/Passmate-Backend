package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kr.passmate.room.domain.RoomType
import java.time.LocalDateTime

@Schema(description = "방 생성 요청")
data class RoomCreateRequest(
    @field:NotBlank(message = "방 이름은 필수입니다.")
    @field:Size(max = 100)
    val title: String,

    @field:Schema(description = "무료/유료 — 현재는 FREE 만 지원", defaultValue = "FREE")
    val type: RoomType = RoomType.FREE,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Schema(description = "주제 태그 (백엔드 · CS 면접 · 네트워크 …)")
    @field:Size(max = 50)
    val topic: String? = null,

    @field:Schema(description = "연결할 문제 세트. 없으면 개설 후 에디터에서 만든다")
    val questionSetId: Long? = null,

    @field:Schema(description = "참가비(코인, 1 C = 1원). 유료 방에서만 쓴다")
    @field:Min(0)
    val fee: Int? = null,

    @field:Schema(description = "최대 인원. 비우면 제한 없음")
    @field:Min(1)
    @field:Max(1000)
    val maxParticipants: Int? = null,

    @field:Schema(description = "공개 방 목록에 노출할지")
    val isPublic: Boolean = false,

    val scheduledAt: LocalDateTime? = null,
)

@Schema(description = "방 정보 수정 요청 — 대기 중일 때만 가능")
data class RoomUpdateRequest(
    @field:NotBlank(message = "방 이름은 필수입니다.")
    @field:Size(max = 100)
    val title: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Size(max = 50)
    val topic: String? = null,

    val questionSetId: Long? = null,

    @field:Min(1)
    @field:Max(1000)
    val maxParticipants: Int? = null,

    val isPublic: Boolean = false,

    val scheduledAt: LocalDateTime? = null,
)

@Schema(description = "방 입장 요청")
data class JoinRoomRequest(
    @field:NotBlank(message = "닉네임은 필수입니다.")
    @field:Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
    val nickname: String,

    @field:Schema(description = "캐릭터. 회원이 비우면 마이페이지 기본 캐릭터를 쓴다")
    @field:Size(max = 30)
    val avatarId: String? = null,

    @field:Schema(description = "게스트 제재를 기기 기준으로 걸기 위한 값")
    @field:Size(max = 64)
    val deviceKey: String? = null,
)
