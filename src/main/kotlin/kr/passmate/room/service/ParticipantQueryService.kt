package kr.passmate.room.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.ParticipantStatus
import kr.passmate.room.repository.ParticipantRepository
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 닉네임 중복 확인 결과. 중복이면 바로 쓸 수 있는 대안을 함께 준다. */
data class NicknameCheckResult(
    val available: Boolean,
    val suggestions: List<String>,
)

@Service
@Transactional(readOnly = true)
class ParticipantQueryService(
    private val roomRepository: RoomRepository,
    private val participantRepository: ParticipantRepository,
) {

    /** 대기실 참가자 목록. 나간·내보내진 사람은 빼고 입장 순으로 준다. */
    fun listJoined(roomId: Long): List<Participant> {
        verifyRoomExists(roomId)
        return participantRepository
            .findAllByRoomIdAndStatusOrderByJoinedAtAsc(roomId, ParticipantStatus.JOINED)
    }

    /** 대기실 참가자 목록(REST) — 방에 속한 사람(호스트·참가자)만 본다. */
    fun listJoined(roomId: Long, principal: AuthPrincipal): List<Participant> {
        val room = roomRepository.findById(roomId)
            .orElseThrow { BusinessException(ErrorCode.ROOM_NOT_FOUND) }
        verifyBelongsToRoom(room, principal)
        return listJoined(roomId)
    }

    /**
     * 방의 호스트 또는 입장한 참가자만 통과시킨다.
     * 게스트 토큰은 발급받은 방 하나에만 유효하다 — STOMP 구독 인가(RoomSubscriptionAuthorizer)와 같은 규칙.
     */
    fun verifyBelongsToRoom(room: Room, principal: AuthPrincipal) {
        val allowed = when (principal) {
            is UserPrincipal -> principal.userId == room.hostUserId || isJoined(room.id, principal.userId)
            is GuestPrincipal -> principal.roomId == room.id
        }
        if (!allowed) throw BusinessException(ErrorCode.ACCESS_DENIED, "이 방에 입장한 사람만 볼 수 있습니다.")
    }

    /**
     * 방에 들어왔던 사람 전부(나간 사람 포함). 결과·리포트가 쓴다 —
     * 중도 이탈자도 낸 답안과 점수가 있어서 목록에서 빼면 합계가 어긋난다.
     */
    fun listAll(roomId: Long): List<Participant> {
        verifyRoomExists(roomId)
        return participantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId)
    }

    /** 내가 참가자로 들어갔던 기록 전부(최근 순). 누적 리포트·참여한 방 목록이 쓴다. */
    fun listByUser(userId: Long): List<Participant> =
        participantRepository.findAllByUserIdOrderByJoinedAtDesc(userId)

    /** 참가자 한 명. 방을 모르는 경로(신고 접수)가 존재만 확인할 때 쓴다. */
    fun get(participantId: Long): Participant =
        participantRepository.findById(participantId)
            .orElseThrow { BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND) }

    /** 그 방의 참가자 한 명. 다른 방 참가자 id 로는 찾히지 않는다. */
    fun getOfRoom(roomId: Long, participantId: Long): Participant =
        participantRepository.findById(participantId)
            .filter { it.roomId == roomId }
            .orElseThrow { BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND) }

    /**
     * 닉네임은 방 안에서만 유일하다(uk_participant_nickname).
     * 이미 쓰이고 있으면 뒤에 숫자를 붙여 비어 있는 것을 최대 3개 제안한다.
     */
    fun checkNickname(roomId: Long, rawNickname: String): NicknameCheckResult {
        verifyRoomExists(roomId)
        val nickname = rawNickname.trim()
        if (!participantRepository.existsByRoomIdAndNickname(roomId, nickname)) {
            return NicknameCheckResult(available = true, suggestions = emptyList())
        }

        val taken = participantRepository
            .findAllByRoomIdAndNicknameStartingWith(roomId, nickname)
            .map { it.nickname }
            .toSet()

        val suggestions = (2..MAX_SUFFIX)
            .map { suffix -> nickname.take(MAX_NICKNAME_LENGTH - suffix.toString().length) + suffix }
            .filter { it !in taken }
            .take(SUGGESTION_COUNT)

        return NicknameCheckResult(available = false, suggestions = suggestions)
    }

    /** 이 회원이 지금 이 방에 들어와 있는지. STOMP 구독 인가에서 쓴다. */
    fun isJoined(roomId: Long, userId: Long): Boolean =
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED) != null

    private fun verifyRoomExists(roomId: Long) {
        if (!roomRepository.existsById(roomId)) throw BusinessException(ErrorCode.ROOM_NOT_FOUND)
    }

    companion object {
        private const val MAX_NICKNAME_LENGTH = 30
        private const val MAX_SUFFIX = 30
        private const val SUGGESTION_COUNT = 3
    }
}
