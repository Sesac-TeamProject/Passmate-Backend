package kr.passmate.ai.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * AI 생성 호출 기록. **무료 한도의 근거**라 성공·실패를 모두 남긴다.
 *
 * 실패는 무료 횟수를 깎지 않는다 — 그래서 한도 집계는 status=SUCCESS 만 센다.
 * 생성이든 재생성이든 호출은 호출이라, kind 는 가리지 않고 함께 센다.
 * Redis 카운터를 쓰지 않는 이유는 이 표가 이미 진실이기 때문이다(2차 도입 시 캐시로만 얹는다).
 */
@Entity
@Table(name = "ai_generation_log")
class AiGenerationLog(
    @Column(name = "set_id", nullable = false, updatable = false)
    val setId: Long,

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    val kind: AiGenerationKind,

    /** 요청 조건. 재생성·재현·과금 문의 때 무엇을 시켰는지 되짚는 근거가 된다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params")
    val params: Map<String, Any?>? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: AiGenerationStatus,

    @Column(name = "retry_count", nullable = false)
    val retryCount: Int = 0,

    @Column(name = "error_message", length = ERROR_MESSAGE_MAX)
    val errorMessage: String? = null,

    @Column(name = "model", length = 50)
    val model: String? = null,

    @Column(name = "duration_ms")
    val durationMs: Int? = null,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    companion object {
        const val ERROR_MESSAGE_MAX = 500

        /** 컬럼 길이를 넘기면 저장이 통째로 실패한다 — 로그 때문에 요청을 죽이지 않는다. */
        fun truncate(message: String?): String? = message?.take(ERROR_MESSAGE_MAX)
    }
}
