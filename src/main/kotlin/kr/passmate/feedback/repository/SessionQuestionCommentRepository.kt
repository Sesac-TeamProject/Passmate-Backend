package kr.passmate.feedback.repository

import kr.passmate.feedback.domain.SessionQuestionComment
import org.springframework.data.jpa.repository.JpaRepository

interface SessionQuestionCommentRepository : JpaRepository<SessionQuestionComment, Long> {

    fun findBySessionQuestionId(sessionQuestionId: Long): SessionQuestionComment?

    /** 방 리포트가 문항 전체의 코멘트를 한 번에 붙일 때 쓴다 — 문항마다 조회하면 N+1 이다. */
    fun findAllBySessionQuestionIdIn(sessionQuestionIds: Collection<Long>): List<SessionQuestionComment>
}
