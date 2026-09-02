package kr.passmate.session.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 참가자가 낸 답안. (participant, session_question) 로 유니크 — 한 문항에 한 번만 낸다.
 *
 * 점수는 두 벌이다.
 * - `score`      : 제출 즉시 매기는 잠정 점수. 랭킹에 바로 반영된다
 * - `finalScore` : AI 분석·선생님 첨삭으로 보정된 최종 점수 (서술형만 달라진다)
 */
@Entity
@Table(name = "answer")
class Answer(
    @Column(name = "participant_id", nullable = false, updatable = false)
    val participantId: Long,

    @Column(name = "session_question_id", nullable = false, updatable = false)
    val sessionQuestionId: Long,

    @Column(name = "submitted", nullable = false, columnDefinition = "TEXT")
    val submitted: String,

    @Column(name = "submitted_at", nullable = false, updatable = false)
    val submittedAt: LocalDateTime,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    /** 서술형은 채점 전이라 null 로 둔다. */
    @Column(name = "is_correct")
    var isCorrect: Boolean? = null
        protected set

    @Column(name = "remaining_ratio", precision = 5, scale = 4)
    var remainingRatio: BigDecimal? = null
        protected set

    @Column(name = "base_score", nullable = false)
    var baseScore: Int = 0
        protected set

    @Column(name = "speed_bonus", nullable = false)
    var speedBonus: Int = 0
        protected set

    @Column(name = "score", nullable = false)
    var score: Int = 0
        protected set

    @Column(name = "final_score", nullable = false)
    var finalScore: Int = 0
        protected set

    /** 채점 결과를 반영한다. 보정 전이므로 final 도 같은 값으로 시작한다. */
    fun applyScore(isCorrect: Boolean?, remainingRatio: BigDecimal, baseScore: Int, speedBonus: Int) {
        this.isCorrect = isCorrect
        this.remainingRatio = remainingRatio
        this.baseScore = baseScore
        this.speedBonus = speedBonus
        this.score = baseScore + speedBonus
        this.finalScore = this.score
    }

    /** AI 분석·첨삭으로 서술형 점수를 보정한다(scoring/feedback 기능에서 부른다). */
    fun adjustFinalScore(finalScore: Int) {
        this.finalScore = finalScore
    }
}
