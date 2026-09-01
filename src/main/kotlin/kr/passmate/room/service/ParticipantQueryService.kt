package kr.passmate.room.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.room.domain.Participant
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
