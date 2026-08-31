package kr.passmate.room.repository

import jakarta.persistence.LockModeType
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RoomRepository : JpaRepository<Room, Long> {

    /** PIN 은 활성 방(WAITING·RUNNING) 사이에서만 유일하다. 종료된 방의 PIN 은 재사용된다. */
    fun existsByPinAndStatusIn(pin: String, statuses: Collection<RoomStatus>): Boolean

    fun findByPinAndStatusIn(pin: String, statuses: Collection<RoomStatus>): Room?

    /**
     * 입장 처리용 비관적 락. 정원 확인과 participant_count 증가 사이에
     * 다른 입장이 끼어들어 정원을 넘기는 것을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Room?
}
