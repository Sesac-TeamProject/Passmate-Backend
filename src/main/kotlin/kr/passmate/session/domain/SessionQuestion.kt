package kr.passmate.session.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime

/**
 * 세션에서 실제로 출제된 문항 한 개.
 *
 * 문제 세트의 `question` 을 그대로 쓰지 않고 복사해 두는 이유:
 * 세션마다 시작·마감 시각과 집계가 달라지고, 세트가 여러 방에 재사용되기 때문이다.
 *
 * **`endsAt` 은 서버가 발급한다.** 클라이언트가 보낸 시각을 믿으면 시계를 늦춰 시간을 벌 수 있다.
 */
@Entity
@Table(name = "session_question")
class SessionQuestion(
    @Column(name = "room_id", nullable = false, updatable = false)
    val roomId: Long,

    @Column(name = "question_id", nullable = false, updatable = false)
    val questionId: Long,

    @Column(name = "order_no", nullable = false, updatable = false)
    val orderNo: Int,

    @Column(name = "time_limit_sec", nullable = false, updatable = false)
    val timeLimitSec: Int,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null
        protected set

    /** 서버 권위 마감 시각. 클라이언트는 이 값을 받아 남은 시간을 표시만 한다. */
    @Column(name = "ends_at")
    var endsAt: LocalDateTime? = null
        protected set

    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null
        protected set

    @Column(name = "submit_count", nullable = false)
    var submitCount: Int = 0
        protected set

    @Column(name = "correct_count", nullable = false)
    var correctCount: Int = 0
        protected set

    @Column(name = "correct_rate", precision = 5, scale = 2)
    var correctRate: BigDecimal? = null
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_distribution")
    var answerDistribution: Map<String, Int>? = null
        protected set

    val isRunning: Boolean get() = startedAt != null && endedAt == null
    val isEnded: Boolean get() = endedAt != null

    fun start(now: LocalDateTime = LocalDateTime.now()) {
        if (startedAt != null) throw BusinessException(ErrorCode.CONFLICT, "이미 시작된 문항입니다.")
        startedAt = now
        endsAt = now.plusSeconds(timeLimitSec.toLong())
    }

    /** 마감. 집계는 마감 시점에 한 번만 계산해 박아둔다. */
    fun end(submitCount: Int, correctCount: Int, distribution: Map<String, Int>, now: LocalDateTime = LocalDateTime.now()) {
        if (endedAt != null) return // 타이머와 호스트 조작이 겹쳐도 한 번만 마감되게 (멱등)
        endedAt = now
        this.submitCount = submitCount
        this.correctCount = correctCount
        this.answerDistribution = distribution
        this.correctRate = if (submitCount == 0) BigDecimal.ZERO
        else BigDecimal(correctCount * 100.0 / submitCount).setScale(2, RoundingMode.HALF_UP)
    }

    /** 제한시간이 지났는지. 타이머가 이걸로 마감 대상을 고른다. */
    fun isExpired(at: LocalDateTime = LocalDateTime.now()): Boolean =
        endedAt == null && endsAt?.isBefore(at) == true

    /**
     * 제출 시점의 남은 시간 비율(0.0~1.0). 속도 보너스의 재료다.
     * 서버가 받은 시각으로만 계산한다 — 클라이언트가 보낸 시각은 쓰지 않는다.
     */
    fun remainingRatio(at: LocalDateTime): BigDecimal {
        val end = endsAt ?: return BigDecimal.ZERO
        val remain = Duration.between(at, end).toMillis()
        if (remain <= 0) return BigDecimal.ZERO
        val total = timeLimitSec * 1000L
        return BigDecimal(remain.coerceAtMost(total).toDouble() / total).setScale(4, RoundingMode.HALF_UP)
    }
}
