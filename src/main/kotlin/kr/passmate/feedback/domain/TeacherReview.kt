package kr.passmate.feedback.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.time.LocalDateTime

/**
 * 선생님 첨삭. 답안당 하나(uk_teacher_review_answer)다.
 *
 * AI 피드백과 **나란히 보이되 구분해서** 내보낸다 — 학생이 "사람이 본 것"과
 * "기계가 본 것"을 섞어 읽으면 첨삭의 무게가 사라진다.
 *
 * [adjustedScore] 는 서술형 보정 점수다. 값이 있으면 `answer.final_score` 가 이걸로 바뀐다.
 * 등록·수정 API 는 P3 라 지금은 조회만 한다.
 */
@Entity
@Table(name = "teacher_review")
class TeacherReview(
    @Column(name = "answer_id", nullable = false, updatable = false)
    val answerId: Long,

    @Column(name = "reviewer_user_id", nullable = false, updatable = false)
    val reviewerUserId: Long,

    @Column(name = "comment", columnDefinition = "TEXT")
    var comment: String? = null,

    /** 서술형 보정 점수. null 이면 점수는 그대로 두고 코멘트만 단 것 */
    @Column(name = "adjusted_score")
    var adjustedScore: Int? = null,

    @Column(name = "improvement", columnDefinition = "TEXT")
    var improvement: String? = null,

    @Column(name = "reviewed_at", nullable = false)
    var reviewedAt: LocalDateTime = LocalDateTime.now(),
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set
}
