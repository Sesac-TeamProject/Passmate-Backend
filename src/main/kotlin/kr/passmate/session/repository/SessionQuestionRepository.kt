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
}
