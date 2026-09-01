package kr.passmate.feedback.repository

import kr.passmate.feedback.domain.TeacherReview
import org.springframework.data.jpa.repository.JpaRepository

interface TeacherReviewRepository : JpaRepository<TeacherReview, Long> {

    fun findByAnswerId(answerId: Long): TeacherReview?

    /** 여러 답안의 첨삭을 한 번에 — 결과 화면은 문항 수만큼 답안을 훑는다(N+1 회피). */
    fun findAllByAnswerIdIn(answerIds: Collection<Long>): List<TeacherReview>
}
