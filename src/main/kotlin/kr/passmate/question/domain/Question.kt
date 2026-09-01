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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 문항. 세트 안에서 order_no 로 순서가 정해진다(uk_question_order).
 * 유형별 필수 조건(객관식 보기, OX 정답 등)은 이 클래스가 스스로 지킨다.
 */
@Entity
@Table(name = "question")
class Question(
    @Column(name = "set_id", nullable = false, updatable = false)
    val setId: Long,

    @Column(name = "order_no", nullable = false)
    var orderNo: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    var type: QuestionType,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices")
    var choices: List<String>? = null,

    @Column(name = "answer", length = 500)
    var answer: String? = null,

    @Column(name = "explanation", columnDefinition = "TEXT")
    var explanation: String? = null,

    @Column(name = "topic", length = 100)
    var topic: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 10)
    var difficulty: Difficulty? = null,

    @Column(name = "time_limit_sec", nullable = false)
    var timeLimitSec: Int,

    @Column(name = "points", nullable = false)
    var points: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    val source: QuestionSource,
) : BaseTimeEntity() {

    init {
        validate()
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
        protected set

    fun edit(
        type: QuestionType,
        content: String,
        choices: List<String>?,
        answer: String?,
        explanation: String?,
        topic: String?,
        difficulty: Difficulty?,
        timeLimitSec: Int,
        points: Int,
    ) {
        this.type = type
        this.content = content
        this.choices = choices
        this.answer = answer
        this.explanation = explanation
        this.topic = topic
        this.difficulty = difficulty
        this.timeLimitSec = timeLimitSec
        this.points = points
        validate()
    }

    fun changeOrder(orderNo: Int) {
        this.orderNo = orderNo
    }

    /** 유형별 필수 조건. 어긋나면 400 으로 막는다. */
    private fun validate() {
        if (timeLimitSec !in MIN_TIME_LIMIT_SEC..MAX_TIME_LIMIT_SEC) {
            throw BusinessException(
                ErrorCode.INVALID_QUESTION,
                "제한시간은 ${MIN_TIME_LIMIT_SEC}~${MAX_TIME_LIMIT_SEC}초 사이여야 합니다.",
            )
        }
        if (points !in MIN_POINTS..MAX_POINTS) {
            throw BusinessException(
                ErrorCode.INVALID_QUESTION,
                "배점은 ${MIN_POINTS}~${MAX_POINTS}점 사이여야 합니다.",
            )
        }
        when (type) {
            QuestionType.MCQ -> {
                val options = choices.orEmpty()
                if (options.size < MIN_CHOICES) {
                    throw BusinessException(ErrorCode.INVALID_QUESTION, "객관식은 보기가 ${MIN_CHOICES}개 이상이어야 합니다.")
                }
                if (answer !in options) {
                    throw BusinessException(ErrorCode.INVALID_QUESTION, "정답은 보기 중 하나여야 합니다.")
                }
            }

            QuestionType.OX -> {
                if (answer !in OX_ANSWERS) {
                    throw BusinessException(ErrorCode.INVALID_QUESTION, "OX 정답은 O 또는 X 여야 합니다.")
                }
            }

            // 서술형은 정답을 채점 기준으로 쓴다 — 비어 있으면 AI 분석이 기준을 잃는다
            QuestionType.ESSAY -> {
                if (answer.isNullOrBlank()) {
                    throw BusinessException(ErrorCode.INVALID_QUESTION, "서술형은 모범답안이 필요합니다.")
                }
            }
        }
    }

    companion object {
        const val MIN_TIME_LIMIT_SEC = 5
        const val MAX_TIME_LIMIT_SEC = 600
        const val MIN_POINTS = 1
        const val MAX_POINTS = 1000
        const val MIN_CHOICES = 2
        val OX_ANSWERS = setOf("O", "X")
    }
}
