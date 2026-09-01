package kr.passmate.feedback.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 서술형 답안 한 개에 대한 AI 분석. 답안당 하나(uk_ai_feedback_answer)다.
 *
 * **자동으로 만들지 않는다** — 학생이 "AI 분석 보기"를 눌렀을 때만 생긴다(FR-075).
 * 자동 실행이면 분석 비용에 상한이 없어진다.
 *
 * [userId] 는 answer → participant 로 따라갈 수 있지만, 월 무료 한도를 세려고 비정규화했다.
 */
@Entity
@Table(name = "ai_feedback")
class AiFeedback(
    @Column(name = "answer_id", nullable = false, updatable = false)
    val answerId: Long,

    /** 분석을 요청·결제한 회원. 게스트는 요청할 수 없다 */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    chargedCoins: Int = 0,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: AiFeedbackStatus = AiFeedbackStatus.PENDING
        protected set

    /**
     * 이 건에 실제로 부담한 코인. **0 이면 월 무료 한도로 처리된 건**이다.
     * 실패해서 환급하면 다시 0 으로 돌아간다 — 부담이 사라졌으므로.
     * 차감·환급의 이력 자체는 코인 원장(coin_transaction)에 남는다.
     */
    @Column(name = "charged_coins", nullable = false)
    var chargedCoins: Int = chargedCoins
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_points")
    var keyPoints: List<String>? = null
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_points")
    var missingPoints: List<String>? = null
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestions")
    var suggestions: List<String>? = null
        protected set

    @Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null
        protected set

    @Column(name = "model", length = 50)
    var model: String? = null
        protected set

    @Column(name = "latency_ms")
    var latencyMs: Int? = null
        protected set

    @Column(name = "error_message", length = ERROR_MESSAGE_MAX)
    var errorMessage: String? = null
        protected set

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
        protected set

    val isPending: Boolean get() = status == AiFeedbackStatus.PENDING
    val isFailed: Boolean get() = status == AiFeedbackStatus.FAILED

    fun complete(
        keyPoints: List<String>,
        missingPoints: List<String>,
        suggestions: List<String>,
        summary: String,
        model: String,
        latencyMs: Int,
        at: LocalDateTime = LocalDateTime.now(),
    ) {
        this.keyPoints = keyPoints
        this.missingPoints = missingPoints
        this.suggestions = suggestions
        this.summary = summary
        this.model = model
        this.latencyMs = latencyMs
        this.errorMessage = null
        this.status = AiFeedbackStatus.DONE
        this.completedAt = at
    }

    fun fail(message: String?, at: LocalDateTime = LocalDateTime.now()) {
        this.status = AiFeedbackStatus.FAILED
        this.errorMessage = truncate(message)
        this.completedAt = at
    }

    /** 환급이 끝났다. 부담이 사라졌으므로 무료 건과 같은 0 으로 되돌린다. */
    fun clearCharge() {
        this.chargedCoins = 0
    }

    /**
     * 실패한 건을 같은 답안으로 다시 요청한다. 답안당 한 줄이라 새로 만들지 않고 이 줄을 되쓴다.
     * 차감액은 그 시점의 무료 한도로 다시 계산해서 넘어온다.
     */
    fun retry(chargedCoins: Int) {
        this.status = AiFeedbackStatus.PENDING
        this.chargedCoins = chargedCoins
        this.errorMessage = null
        this.completedAt = null
    }

    companion object {
        const val ERROR_MESSAGE_MAX = 500

        /** 컬럼 길이를 넘기면 저장이 통째로 실패한다 — 로그 때문에 분석 결과를 잃지 않는다. */
        fun truncate(message: String?): String? = message?.take(ERROR_MESSAGE_MAX)
    }
}
