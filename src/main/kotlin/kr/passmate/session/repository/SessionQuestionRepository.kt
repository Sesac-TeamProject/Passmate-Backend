package kr.passmate.session.repository

import kr.passmate.session.domain.SessionQuestion
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface SessionQuestionRepository : JpaRepository<SessionQuestion, Long> {

    fun findAllByRoomIdOrderByOrderNoAsc(roomId: Long): List<SessionQuestion>

    fun findByRoomIdAndOrderNo(roomId: Long, orderNo: Int): SessionQuestion?

    fun countByRoomId(roomId: Long): Int

    fun findByRoomIdAndQuestionId(roomId: Long, questionId: Long): SessionQuestion?

    /** 제한시간이 지났는데 아직 안 닫힌 문항. 서버 권위 타이머가 이걸 골라 마감한다. */
    fun findAllByEndedAtIsNullAndEndsAtLessThan(now: LocalDateTime): List<SessionQuestion>

    /**
     * 자동 넘김 기한이 온 문항 — 마감된 지 결과 표시 시간이 지났고, 방이 아직 그 문항에 머물러 있는 것만.
     * 호스트가 이미 넘긴 방은 currentQuestionNo 가 달라 걸리지 않고, 마지막 문항은
     * advanceByAutoAdvance 가 세션을 끝내 RUNNING 필터에서 빠진다 —
     * 매초 도는 폴링이 지난 세션을 계속 집지 않는다.
     */
    @org.springframework.data.jpa.repository.Query(
        """
        select sq from SessionQuestion sq
        join Room r on r.id = sq.roomId
        where sq.autoAdvance = true
          and sq.endedAt is not null and sq.endedAt <= :deadline
          and r.status = kr.passmate.room.domain.RoomStatus.RUNNING
          and r.currentQuestionNo = sq.orderNo
        """,
    )
    fun findAutoAdvanceDue(
        @org.springframework.data.repository.query.Param("deadline") deadline: LocalDateTime,
    ): List<SessionQuestion>
}
