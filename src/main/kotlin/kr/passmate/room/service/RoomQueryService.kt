package kr.passmate.room.service

import kr.passmate.common.config.ClientProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.util.QrCodeGenerator
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.room.domain.Room
import kr.passmate.room.dto.RoomQuestionTimeView
import kr.passmate.room.dto.RoomQuestionTimesResponse
import kr.passmate.room.dto.RoomResponse
import kr.passmate.room.repository.RoomRepository
import kr.passmate.user.service.UserQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RoomQueryService(
    private val roomRepository: RoomRepository,
    private val participantQueryService: ParticipantQueryService,
    private val qrCodeGenerator: QrCodeGenerator,
    private val clientProperties: ClientProperties,
    private val userQueryService: UserQueryService,
    private val questionSetQueryService: QuestionSetQueryService,
) {

    /**
     * 방 상세 응답. 호스트 닉네임과 연결 세트 요약(문항 수·소요시간·문항당 제한)을 붙인다 —
     * 대기실이 세트 목록·상세를 따로 읽지 않게(웹 버그 리포트 B-21). 세트 내용(문항·정답)은 싣지 않는다.
     */
    fun toResponse(room: Room): RoomResponse {
        val nickname = userQueryService.getNicknames(listOf(room.hostUserId))[room.hostUserId]
        val stats = room.questionSetId?.let { questionSetQueryService.findStats(it) }
        return RoomResponse.of(
            room = room,
            hostNickname = nickname,
            questionCount = stats?.questionCount,
            estimatedSeconds = stats?.estimatedSeconds,
            minTimeLimitSec = stats?.minTimeLimitSec,
            maxTimeLimitSec = stats?.maxTimeLimitSec,
        )
    }

    /**
     * 문항별 시간 화면(W-02b). 세트 문항에 이 방이 덮어쓴 시간을 얹어 준다. 호스트만 —
     * 세트는 호스트 소유라 방 문맥에서만 호스트 자격으로 꺼낸다. 정답·해설은 싣지 않는다.
     */
    fun questionTimes(roomId: Long, hostUserId: Long): RoomQuestionTimesResponse {
        val room = getRoom(roomId)
        room.verifyHost(hostUserId)
        val setId = room.questionSetId ?: throw BusinessException(ErrorCode.QUESTION_SET_REQUIRED)
        val (_, questions) = questionSetQueryService.getDetail(setId, room.hostUserId)

        val views = questions.map { question ->
            RoomQuestionTimeView(
                questionId = question.id,
                orderNo = question.orderNo,
                type = question.type,
                content = question.content,
                defaultTimeLimitSec = question.timeLimitSec,
                timeLimitSec = room.timeLimitSecOf(question.id, question.timeLimitSec),
                overridden = room.hasTimeOverride(question.id),
            )
        }
        return RoomQuestionTimesResponse(
            roomId = room.id,
            questionSetId = setId,
            estimatedSeconds = views.sumOf { it.timeLimitSec },
            questions = views,
        )
    }

    /**
     * 아직 안 끝난 방(대기·진행)이 이 문제 세트를 쓰고 있는지.
     *
     * 문제 세트 삭제가 그 방의 출제 근거를 지워 버리면 세션을 시작할 수 없게 된다 —
     * question 기능이 삭제 전에 여기로 물어본다.
     */
    fun isUsedByActiveRoom(questionSetId: Long): Boolean =
        roomRepository.existsByQuestionSetIdAndStatusIn(questionSetId, PinService.ACTIVE_STATUSES)

    fun getRoom(roomId: Long): Room =
        roomRepository.findById(roomId)
            .orElseThrow { BusinessException(ErrorCode.ROOM_NOT_FOUND) }

    /** 방 상세(REST) — 응답에 PIN 이 담기므로 방에 속한 사람(호스트·참가자)만 본다. */
    fun getRoomDetail(roomId: Long, principal: AuthPrincipal): Room {
        val room = getRoom(roomId)
        participantQueryService.verifyBelongsToRoom(room, principal)
        return room
    }

    /** PIN 은 활성 방 사이에서만 유일하다. 종료된 방은 조회되지 않는다. */
    /** 방 여러 개를 한 번에. 목록 화면이 방마다 조회하면 그대로 N+1 이다. */
    fun getRooms(roomIds: Collection<Long>): Map<Long, Room> {
        if (roomIds.isEmpty()) return emptyMap()
        return roomRepository.findAllById(roomIds.toSet()).associateBy { it.id }
    }

    fun getActiveRoomByPin(pin: String): Room =
        roomRepository.findByPinAndStatusIn(pin, PinService.ACTIVE_STATUSES)
            // 활성 방이 없을 때: 쓰였던 PIN 이면 "이미 끝난 방"(410), 아니면 "없는 PIN"(404).
            // 화면이 안내 문구를 가르는 근거(웹 QA_BACKLOG B-4). PIN 재사용 시엔 위 활성 조회가 우선한다
            ?: throw when {
                roomRepository.existsByPin(pin) -> BusinessException(ErrorCode.ROOM_ENDED)
                else -> BusinessException(ErrorCode.ROOM_NOT_FOUND, "해당 PIN 의 방이 없습니다.")
            }

    /** 입장 링크를 담은 QR PNG. 호스트만 받을 수 있다. */
    fun getQrPng(roomId: Long, hostUserId: Long): ByteArray {
        val room = getRoom(roomId)
        room.verifyHost(hostUserId)
        return qrCodeGenerator.toPngBytes(clientProperties.joinUrl(room.pin))
    }
}
