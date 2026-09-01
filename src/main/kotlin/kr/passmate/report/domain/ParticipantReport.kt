package kr.passmate.report.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/**
 * 세션이 끝날 때 참가자마다 한 장씩 찍어 두는 개인 학습 리포트 (FR-030).
 *
 * 매번 계산해도 나오는 값이지만 **남겨 둔다** — 나중에 문항이 수정되거나 첨삭으로
 * 점수가 바뀌어도 "그때 그 세션에서 내가 받은 결과"는 변하지 않아야 한다.
 *
 * 참가자당 하나(uk_participant_report)라 다시 만들어도 행이 늘지 않는다.
 */
@Entity
@Table(name = "participant_report")
class ParticipantReport(
    @Column(name = "participant_id", nullable = false, updatable = false)
    val participantId: Long,

    totalQuestions: Int,
    correctCount: Int,
    totalScore: Int,
    finalRank: Int,
    weakTopics: List<String>?,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "total_questions", nullable = false)
    var totalQuestions: Int = totalQuestions
        protected set

    @Column(name = "correct_count", nullable = false)
    var correctCount: Int = correctCount
        protected set

    /** 정답률(%). 출제된 문항 수가 분모다 — 미제출도 못 맞힌 것으로 센다 */
    @Column(name = "accuracy", nullable = false, precision = 5, scale = 2)
    var accuracy: BigDecimal = accuracyOf(correctCount, totalQuestions)
        protected set

    @Column(name = "total_score", nullable = false)
    var totalScore: Int = totalScore
        protected set

    @Column(name = "final_rank", nullable = false)
    var finalRank: Int = finalRank
        protected set

    /** 틀리거나 놓친 문항의 주제. 문항에 주제가 적혀 있지 않으면 비어 있다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weak_topics")
    var weakTopics: List<String>? = weakTopics
        protected set

    @Column(name = "generated_at", nullable = false)
    var generatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    /** 같은 참가자의 리포트를 다시 찍는다. 행을 늘리지 않고 덮어써서 멱등하게 만든다. */
    fun refresh(
        totalQuestions: Int,
        correctCount: Int,
        totalScore: Int,
        finalRank: Int,
        weakTopics: List<String>?,
        at: LocalDateTime = LocalDateTime.now(),
    ) {
        this.totalQuestions = totalQuestions
        this.correctCount = correctCount
        this.accuracy = accuracyOf(correctCount, totalQuestions)
        this.totalScore = totalScore
        this.finalRank = finalRank
        this.weakTopics = weakTopics
        this.generatedAt = at
    }

    companion object {
        fun accuracyOf(correctCount: Int, totalQuestions: Int): BigDecimal =
            if (totalQuestions == 0) BigDecimal.ZERO
            else BigDecimal(correctCount * 100.0 / totalQuestions).setScale(2, RoundingMode.HALF_UP)
    }
}
