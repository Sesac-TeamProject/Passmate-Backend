package kr.passmate.session.repository

import kr.passmate.session.domain.Answer
import org.springframework.data.jpa.repository.JpaRepository

interface AnswerRepository : JpaRepository<Answer, Long> {

    fun existsByParticipantIdAndSessionQuestionId(participantId: Long, sessionQuestionId: Long): Boolean

    fun findByParticipantIdAndSessionQuestionId(participantId: Long, sessionQuestionId: Long): Answer?

    fun findAllBySessionQuestionId(sessionQuestionId: Long): List<Answer>

    fun findAllByParticipantId(participantId: Long): List<Answer>
}
