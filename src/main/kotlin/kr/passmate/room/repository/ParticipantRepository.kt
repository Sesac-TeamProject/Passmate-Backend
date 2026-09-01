package kr.passmate.room.repository

import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.ParticipantStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    /** 내가 참가자로 들어갔던 기록 전부. 누적 리포트·참여한 방 목록이 쓴다. */
    fun findAllByUserIdOrderByJoinedAtDesc(userId: Long): List<Participant>

    /** 참여한 방 수. 같은 방에 두 번 입장할 수 없어 참가자 행 수 = 방 수다. */
    fun countByUserId(userId: Long): Long

    /**
     * 방별로 들어왔던 사람 수. `room.participant_count` 는 퇴장하면 줄어들어
     * "이 세션에 몇 명이 참여했나"를 답하지 못한다.
     */
    @Query(
        """
        select new kr.passmate.room.repository.RoomParticipantCount(p.roomId, count(p))
        from Participant p
        where p.roomId in :roomIds
        group by p.roomId
        """,
    )
    fun countByRoomIds(@Param("roomIds") roomIds: Collection<Long>): List<RoomParticipantCount>
}

/** 방 하나에 들어왔던 사람 수. */
data class RoomParticipantCount(
    val roomId: Long,
    val count: Long,
)
