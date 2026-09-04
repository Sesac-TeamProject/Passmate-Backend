package kr.passmate.voicehint.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.common.storage.StorageClient
import kr.passmate.common.storage.StorageException
import kr.passmate.common.storage.StorageProperties
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.domain.SessionEventType
import kr.passmate.session.service.SessionEventPublisher
import kr.passmate.session.service.SessionQueryService
import kr.passmate.voicehint.domain.VoiceHint
import kr.passmate.voicehint.dto.VoiceHintListResponse
import kr.passmate.voicehint.dto.VoiceHintResponse
import kr.passmate.voicehint.repository.VoiceHintRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * 실시간 음성 힌트 (FR-039 · FR-040 · FR-041).
 *
 * 호스트가 PTT 로 녹음한 클립을 올리면 저장한 뒤 방 전체에 HINT_PUBLISHED 를 보낸다.
 * 학생 화면은 그걸 받아 3초 이내에 자동 재생하고, 놓치면 목록에서 다시 듣는다.
 *
 * **트랜잭션을 열지 않는다.** S3 업로드가 수 초 걸릴 수 있어 그동안 커넥션을 쥐면
 * 같은 세션의 답안 제출이 굶는다. 저장은 업로드가 끝난 뒤 한 줄이다.
 */
@Service
class VoiceHintService(
    private val roomQueryService: RoomQueryService,
    private val sessionQueryService: SessionQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val storageClient: StorageClient,
    private val storageProperties: StorageProperties,
    private val voiceHintRepository: VoiceHintRepository,
    private val eventPublisher: SessionEventPublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 힌트를 올리고 방 전체에 내보낸다. 호스트만, 문항이 열려 있을 때만. */
    fun publish(roomId: Long, hostUserId: Long, file: MultipartFile, durationMs: Int?): VoiceHintResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        room.verifyRunning()

        // 힌트는 "지금 이 문제"에 대한 것이다. 열린 문항이 없으면 붙일 자리가 없다
        val current = sessionQueryService.currentQuestion(roomId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_RUNNING, "열려 있는 문항이 없어 힌트를 보낼 수 없습니다.")

        val bytes = readAudio(file)
        val key = "rooms/$roomId/hints/${UUID.randomUUID()}.${extensionOf(file.contentType)}"
        val contentType = file.contentType ?: DEFAULT_CONTENT_TYPE

        try {
            storageClient.upload(key, contentType, bytes)
        } catch (e: StorageException) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "음성 힌트를 저장하지 못했습니다.", e)
        }

        val hint = voiceHintRepository.save(
            VoiceHint(
                roomId = roomId,
                sessionQuestionId = current.id,
                audioKey = key,
                durationMs = durationMs,
            ),
        )
        log.info("음성 힌트를 내보냈다 roomId={} sessionQuestionId={} hintId={}", roomId, current.id, hint.id)

        val response = hint.toResponse(current.questionId, current.orderNo)
        eventPublisher.toRoom(roomId, SessionEventType.HINT_PUBLISHED, response)
        return response
    }

    /**
     * 힌트 목록. 학생이 다시 듣기·수동 재생에 쓰고, 호스트는 사용 이력으로 본다.
     * 그 방 사람만 볼 수 있다 — 남의 방 힌트를 들을 이유가 없다.
     */
    @Transactional(readOnly = true)
    fun list(roomId: Long, principal: AuthPrincipal, questionId: Long?): VoiceHintListResponse {
        val room = roomQueryService.getRoom(roomId)
        participantQueryService.verifyBelongsToRoom(room, principal)

        val sessionQuestions = sessionQueryService.sessionQuestions(roomId).associateBy { it.id }
        val hints = questionId
            ?.let { sessionQueryService.findSessionQuestion(roomId, it) }
            ?.let { voiceHintRepository.findAllByRoomIdAndSessionQuestionIdOrderByPublishedAtAsc(roomId, it.id) }
            ?: voiceHintRepository.findAllByRoomIdOrderByPublishedAtAsc(roomId)

        val rows = hints.mapNotNull { hint ->
            val sq = sessionQuestions[hint.sessionQuestionId] ?: return@mapNotNull null
            hint.toResponse(sq.questionId, sq.orderNo)
        }
        return VoiceHintListResponse(roomId = roomId, totalCount = rows.size, hints = rows)
    }

    /** 재생 주소는 응답할 때마다 새로 서명한다 — 저장해 두면 만료된 링크가 남는다. */
    private fun VoiceHint.toResponse(questionId: Long, orderNo: Int) = VoiceHintResponse(
        hintId = id,
        sessionQuestionId = sessionQuestionId,
        questionId = questionId,
        orderNo = orderNo,
        audioUrl = storageClient.presignedUrl(audioKey),
        durationMs = durationMs,
        publishedAt = publishedAt,
    )

    private fun readAudio(file: MultipartFile): ByteArray {
        if (file.isEmpty) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "녹음 파일이 비어 있습니다.")
        }
        if (file.size > storageProperties.maxUploadBytes) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "녹음 파일은 ${storageProperties.maxUploadMb}MB 를 넘을 수 없습니다.",
            )
        }
        val contentType = file.contentType
        if (contentType == null || !contentType.startsWith(AUDIO_PREFIX)) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "오디오 파일만 올릴 수 있습니다.")
        }
        return file.bytes
    }

    /** 확장자는 저장된 파일을 사람이 알아보게 하려는 것뿐이다. 재생은 Content-Type 이 결정한다. */
    private fun extensionOf(contentType: String?): String = when {
        contentType == null -> "bin"
        contentType.startsWith("audio/webm") -> "webm"
        contentType.startsWith("audio/ogg") -> "ogg"
        contentType.startsWith("audio/mpeg") -> "mp3"
        contentType.startsWith("audio/mp4") || contentType.startsWith("audio/aac") -> "m4a"
        contentType.startsWith("audio/wav") || contentType.startsWith("audio/x-wav") -> "wav"
        else -> "bin"
    }

    private companion object {
        const val AUDIO_PREFIX = "audio/"
        const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
    }
}
