package kr.passmate.room.repository

import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.ParticipantStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ParticipantRepository : JpaRepository<Participant, Long> {

    fun existsByRoomIdAndNickname(roomId: Long, nickname: String): Boolean

    fun findAllByRoomIdAndStatusOrderByJoinedAtAsc(
        roomId: Long,
        status: ParticipantStatus,
    ): List<Participant>

    fun findByRoomIdAndUserIdAndStatus(
        roomId: Long,
        userId: Long,
        status: ParticipantStatus,
    ): Participant?

    fun findAllByRoomIdAndNicknameStartingWith(roomId: Long, prefix: String): List<Participant>
}
