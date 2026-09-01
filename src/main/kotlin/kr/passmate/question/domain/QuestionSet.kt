package kr.passmate.question.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import java.time.LocalDateTime

/**
 * 문제 세트. **확정하면 불변**이다(ERD 주석) — 세션 중 문항이 바뀌면 채점 근거가 무너진다.
 * 확정된 세트만 방에 출제할 수 있고, 고치려면 복제해서 새 DRAFT 로 작업한다.
 *
 * question_count·total_points 는 문항의 집계 캐시라 문항이 바뀔 때마다 다시 계산한다.
 */
@Entity
@Table(name = "question_set")
class QuestionSet(
    @Column(name = "owner_user_id", nullable = false, updatable = false)
    val ownerUserId: Long,

    @Column(name = "title", nullable = false, length = 100)
    var title: String,

    @Column(name = "description", length = 500)
    var description: String? = null,

    @Column(name = "duplicated_from_id", updatable = false)
    val duplicatedFromId: Long? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: QuestionSetStatus = QuestionSetStatus.DRAFT
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    var source: ContentSource? = null
        protected set

    @Column(name = "question_count", nullable = false)
    var questionCount: Int = 0
        protected set

    @Column(name = "total_points", nullable = false)
    var totalPoints: Int = 0
        protected set

    @Column(name = "estimated_seconds")
    var estimatedSeconds: Int? = null
        protected set

    @Column(name = "usage_count", nullable = false)
    var usageCount: Int = 0
        protected set

    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null
        protected set

    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    val isDraft: Boolean get() = status == QuestionSetStatus.DRAFT
    val isConfirmed: Boolean get() = status == QuestionSetStatus.CONFIRMED

    fun verifyOwner(userId: Long) {
        if (userId != ownerUserId) throw BusinessException(ErrorCode.NOT_QUESTION_SET_OWNER)
    }

    /** 확정된 세트는 제목 하나도 바꿀 수 없다. 문항 추가·수정·삭제도 전부 이 검사를 거친다. */
    fun verifyEditable() {
        if (isConfirmed) {
            throw BusinessException(
                ErrorCode.QUESTION_SET_ALREADY_CONFIRMED,
                "확정된 세트는 수정할 수 없습니다. 복제해서 새 세트로 작업하세요.",
            )
        }
    }

    fun edit(title: String, description: String?) {
        verifyEditable()
        this.title = title
        this.description = description
    }

    /** 문항이 바뀔 때마다 집계를 다시 맞춘다. */
    fun refreshStats(questions: Collection<Question>) {
        questionCount = questions.size
        totalPoints = questions.sumOf { it.points }
        estimatedSeconds = questions.sumOf { it.timeLimitSec }.takeIf { questions.isNotEmpty() }
        source = ContentSource.of(questions.map { it.source })
    }

    /** 검토를 끝내고 확정한다. 문항이 하나도 없으면 확정할 수 없다. */
    fun confirm(at: LocalDateTime = LocalDateTime.now()) {
        verifyEditable()
        if (questionCount == 0) {
            throw BusinessException(ErrorCode.QUESTION_SET_EMPTY)
        }
        status = QuestionSetStatus.CONFIRMED
        confirmedAt = at
    }

    /** 세션에서 사용됐을 때 호출한다(session 기능에서 부른다). */
    fun markUsed(at: LocalDateTime = LocalDateTime.now()) {
        usageCount += 1
        lastUsedAt = at
    }

    fun delete(at: LocalDateTime = LocalDateTime.now()) {
        deletedAt = at
    }
}
