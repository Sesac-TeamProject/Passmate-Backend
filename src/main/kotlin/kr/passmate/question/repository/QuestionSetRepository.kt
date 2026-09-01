package kr.passmate.question.repository

import kr.passmate.question.domain.QuestionSet
import kr.passmate.question.domain.QuestionSetStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionSetRepository : JpaRepository<QuestionSet, Long> {

    /** 삭제된 세트는 조회하지 않는다(soft delete). */
    fun findByIdAndDeletedAtIsNull(id: Long): QuestionSet?

    fun findAllByOwnerUserIdAndDeletedAtIsNull(ownerUserId: Long, pageable: Pageable): Page<QuestionSet>

    fun findAllByOwnerUserIdAndStatusAndDeletedAtIsNull(
        ownerUserId: Long,
        status: QuestionSetStatus,
        pageable: Pageable,
    ): Page<QuestionSet>
}
