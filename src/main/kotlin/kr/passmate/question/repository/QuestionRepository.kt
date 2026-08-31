package kr.passmate.question.repository

import kr.passmate.question.domain.Question
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionRepository : JpaRepository<Question, Long> {

    fun findAllBySetIdOrderByOrderNoAsc(setId: Long): List<Question>

    fun findByIdAndSetId(id: Long, setId: Long): Question?

    fun findTopBySetIdOrderByOrderNoDesc(setId: Long): Question?
}
