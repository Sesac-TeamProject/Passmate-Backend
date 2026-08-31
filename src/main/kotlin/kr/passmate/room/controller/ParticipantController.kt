package kr.passmate.room.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.dto.JoinRoomResponse
import kr.passmate.room.dto.NicknameCheckResponse
import kr.passmate.room.dto.ParticipantResponse
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.ParticipantService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.validation.annotation.Validated

@Tag(name = "방 입장")
@RestController
@RequestMapping("/rooms/{roomId}/participants")
@Validated
class ParticipantController(
    private val participantService: ParticipantService,
    private val participantQueryService: ParticipantQueryService,
) {

    @Operation(
        summary = "방 입장",
        description = "회원은 계정에 연동되고, 인증 없이 부르면 게스트로 입장해 토큰을 발급받는다.",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun join(
        @CurrentUser(required = false) principal: AuthPrincipal?,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: JoinRoomRequest,
    ): JoinRoomResponse =
        JoinRoomResponse.from(
            participantService.join(roomId, (principal as? UserPrincipal)?.userId, request),
        )

    @Operation(summary = "참가자 목록 조회", description = "대기실 초기 로딩·재접속용. 나간 참가자는 빠진다.")
    @GetMapping
    fun list(@PathVariable roomId: Long): List<ParticipantResponse> =
        participantQueryService.listJoined(roomId).map(ParticipantResponse::from)

    @Operation(
        summary = "닉네임 중복 확인",
        description = "같은 방 안에서만 중복을 본다. 중복이면 대안을 제안한다. 인증 없이 부를 수 있다.",
    )
    @GetMapping("/nickname-check")
    fun checkNickname(
        @PathVariable roomId: Long,
        @RequestParam @NotBlank @Size(max = 30) nickname: String,
    ): NicknameCheckResponse =
        NicknameCheckResponse.from(participantQueryService.checkNickname(roomId, nickname))

    @Operation(summary = "방 퇴장", description = "회원·게스트 모두 자기 자신을 나가게 한다.")
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
    ) {
        participantService.leave(roomId, principal)
    }

    @Operation(summary = "참가자 내보내기", description = "호스트만 할 수 있다.")
    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun kick(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @PathVariable participantId: Long,
    ) {
        participantService.kick(roomId, participantId, principal.userId)
    }
}
