package kr.passmate.voicehint.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.voicehint.dto.VoiceHintListResponse
import kr.passmate.voicehint.dto.VoiceHintResponse
import kr.passmate.voicehint.service.VoiceHintService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 실시간 음성 힌트 — 호스트 PTT(W-05 하단 · M-T2)와 학생 다시 듣기.
 */
@Tag(name = "실시간 음성 힌트")
@RestController
@RequestMapping("/rooms/{roomId}/session/hints")
class VoiceHintController(
    private val voiceHintService: VoiceHintService,
) {

    @Operation(
        summary = "음성 힌트 송출",
        description = "PTT 녹음 클립을 올리면 저장 후 방 전체에 HINT_PUBLISHED 를 보낸다. 호스트만, 문항이 열려 있을 때만.",
    )
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun publish(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) durationMs: Int?,
    ): VoiceHintResponse = voiceHintService.publish(roomId, principal.userId, file, durationMs)

    @Operation(
        summary = "음성 힌트 목록 조회",
        description = "문항별 힌트 클립(재생 주소·송출 시각). 다시 듣기·수동 재생·사용 이력용. 그 방 사람만.",
    )
    @GetMapping
    fun list(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
        @RequestParam(required = false) questionId: Long?,
    ): VoiceHintListResponse = voiceHintService.list(roomId, principal, questionId)
}
