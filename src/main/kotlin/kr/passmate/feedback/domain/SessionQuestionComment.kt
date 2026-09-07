package kr.passmate.feedback.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity

/**
 * 문항 단위 선생님 코멘트 — **학생 전체 대상**이다(W-07 우측 패널, 2026-09-07 B-14).
 *
 * 답안(학생)별 첨삭 [TeacherReview] 와 별개다. 문항당 한 장(uk_sqc_question)이라
 * 다시 저장하면 행을 늘리지 않고 덮어쓴다. 학생 쪽은 M-06 의
 * "선생님 코멘트가 도착하면 여기에 표시돼요" 자리에 내려간다.
 */
@Entity
@Table(name = "session_question_comment")
class SessionQuestionComment(
    @Column(name = "session_question_id", nullable = false, updatable = false)
    val sessionQuestionId: Long,

    @Column(name = "reviewer_user_id", nullable = false, updatable = false)
    val reviewerUserId: Long,

    comment: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
    var comment: String = comment
        protected set

    /** 다시 저장한다(upsert). 문항당 한 장이라 두 번째부터는 이 메서드로 덮어쓴다. */
    fun update(comment: String) {
        this.comment = comment
    }
}
