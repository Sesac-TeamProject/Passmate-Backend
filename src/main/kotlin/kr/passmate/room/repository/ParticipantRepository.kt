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

    /** 나간·내보내진 사람까지 전부. 결과·리포트는 중도 이탈자의 점수도 세야 한다. */
    fun findAllByRoomIdOrderByJoinedAtAsc(roomId: Long): List<Participant>

    /** 참여한 방 수. 같은 방에 두 번 입장할 수 없어 참가자 행 수 = 방 수다. */
    fun countByUserId(userId: Long): Long
}
