package kr.passmate.common.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 모든 엔티티의 공통 시각 컬럼. DDL 의 created_at NOT NULL / updated_at NULL 과 짝을 이룬다.
 * 시각은 UTC 기준으로 저장한다(JVM·MySQL 타임존 모두 UTC).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
}
