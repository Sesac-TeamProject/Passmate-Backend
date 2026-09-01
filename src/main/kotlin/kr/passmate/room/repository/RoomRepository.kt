package kr.passmate.room.repository

import jakarta.persistence.LockModeType
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.domain.RoomType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

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

    fun countByHostUserId(hostUserId: Long): Long

    /** 아직 안 끝난 내 방이 있는지. 탈퇴를 막는 조건이다. */
    fun existsByHostUserIdAndStatusIn(hostUserId: Long, statuses: Collection<RoomStatus>): Boolean

    fun countByHostUserIdAndStatus(hostUserId: Long, status: RoomStatus): Long

    /** 누적 학생 수. 방마다 캐시해 둔 participant_count 를 더한다 — 참가자 행을 다시 세지 않는다. */
    @Query(
        """
        select coalesce(sum(r.participantCount), 0)
        from Room r
        where r.hostUserId = :hostUserId and r.status = :status
        """,
    )
    fun sumParticipantCountByHostAndStatus(
        @Param("hostUserId") hostUserId: Long,
        @Param("status") status: RoomStatus,
    ): Long

    /**
     * 공개 방 목록, 인기순(FR-054). 운영 중인 방을 앞에 두고 참여 인원이 많은 순으로 준다.
     * 종료·취소된 방은 statuses 로 걸러져 나오지 않는다.
     *
     * 파라미터가 비면 그 조건을 건너뛰도록 `:param is null or …` 로 쓴다 —
     * 필터 조합마다 메서드를 만들지 않기 위함이다.
     * 정렬만 다른 메서드가 둘인 이유는 JPQL 이 order by 를 파라미터로 받지 못해서다.
     */
    @Query(
        value = PUBLIC_SELECT + PUBLIC_WHERE + ORDER_BY_POPULAR,
        countQuery = PUBLIC_COUNT + PUBLIC_WHERE,
    )
    fun findPublicByPopularity(
        @Param("statuses") statuses: Collection<RoomStatus>,
        @Param("type") type: RoomType?,
        @Param("q") q: String?,
        @Param("hostIds") hostIds: Collection<Long>,
        @Param("from") from: LocalDateTime?,
        @Param("to") to: LocalDateTime?,
        pageable: Pageable,
    ): Page<Room>

    /** 같은 조건, 예정 시각이 빠른 순. 시각이 없는 방은 뒤로 보낸다. */
    @Query(
        value = PUBLIC_SELECT + PUBLIC_WHERE + ORDER_BY_UPCOMING,
        countQuery = PUBLIC_COUNT + PUBLIC_WHERE,
    )
    fun findPublicByUpcoming(
        @Param("statuses") statuses: Collection<RoomStatus>,
        @Param("type") type: RoomType?,
        @Param("q") q: String?,
        @Param("hostIds") hostIds: Collection<Long>,
        @Param("from") from: LocalDateTime?,
        @Param("to") to: LocalDateTime?,
        pageable: Pageable,
    ): Page<Room>

    companion object {
        private const val PUBLIC_SELECT = "select r from Room r "
        private const val PUBLIC_COUNT = "select count(r) from Room r "

        /** 목록과 count 가 어긋나면 페이지 수가 틀어지므로 조건은 한 곳에만 둔다. */
        private const val PUBLIC_WHERE = """
            where r.isPublic = true
              and r.status in :statuses
              and (:type is null or r.type = :type)
              and (:from is null or (r.scheduledAt >= :from and r.scheduledAt < :to))
              and (:q is null
                   or lower(r.title) like lower(concat('%', :q, '%'))
                   or lower(r.topic) like lower(concat('%', :q, '%'))
                   or r.hostUserId in :hostIds)
        """

        private const val ORDER_BY_POPULAR = """
            order by case when r.status = kr.passmate.room.domain.RoomStatus.RUNNING then 0 else 1 end,
                     r.participantCount desc, r.id desc
        """

        private const val ORDER_BY_UPCOMING = """
            order by case when r.scheduledAt is null then 1 else 0 end, r.scheduledAt asc, r.id desc
        """
    }
}
