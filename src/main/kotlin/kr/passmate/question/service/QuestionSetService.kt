package kr.passmate.question.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.question.domain.Question
import kr.passmate.question.domain.QuestionSet
import kr.passmate.question.domain.QuestionSource
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.dto.QuestionSetUpdateRequest
import kr.passmate.question.repository.QuestionRepository
import kr.passmate.question.repository.QuestionSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionSetService(
    private val questionSetRepository: QuestionSetRepository,
    private val questionRepository: QuestionRepository,
) {

    /** 빈 세트를 만든다. 문항은 이후 직접 추가하거나 AI 로 생성한다. */
    @Transactional
    fun create(ownerUserId: Long, request: QuestionSetCreateRequest): QuestionSet =
        questionSetRepository.save(
            QuestionSet(
                ownerUserId = ownerUserId,
                title = request.title,
                description = request.description,
            ),
        )

    /** 제목·설명과 문항 순서를 바꾼다. 확정 전에만 가능하다. */
    @Transactional
    fun update(setId: Long, ownerUserId: Long, request: QuestionSetUpdateRequest): QuestionSet {
        val set = getEditableSet(setId, ownerUserId)
        set.edit(request.title, request.description)
        request.questionOrder?.let { reorder(setId, it) }
        return set
    }

    @Transactional
    fun confirm(setId: Long, ownerUserId: Long): QuestionSet {
        val set = getEditableSet(setId, ownerUserId)
        // 확정 직전에 집계를 한 번 더 맞춰 둔다 — 확정 후에는 못 고친다
        set.refreshStats(questionRepository.findAllBySetIdOrderByOrderNoAsc(setId))
        set.confirm()
        return set
    }

    @Transactional
    fun addQuestion(setId: Long, ownerUserId: Long, request: QuestionRequest): Question {
        val set = getEditableSet(setId, ownerUserId)
        val nextOrderNo = (questionRepository.findTopBySetIdOrderByOrderNoDesc(setId)?.orderNo ?: 0) + 1

        val question = questionRepository.save(
            Question(
                setId = setId,
                orderNo = nextOrderNo,
                type = request.type,
                content = request.content,
                choices = request.choices,
                answer = request.answer,
                explanation = request.explanation,
                topic = request.topic,
                difficulty = request.difficulty,
                timeLimitSec = request.timeLimitSec,
                points = request.points,
                source = QuestionSource.MANUAL,
            ),
        )
        refreshStats(set)
        return question
    }

    @Transactional
    fun updateQuestion(
        setId: Long,
        questionId: Long,
        ownerUserId: Long,
        request: QuestionRequest,
    ): Question {
        val set = getEditableSet(setId, ownerUserId)
        val question = getQuestion(setId, questionId)
        question.edit(
            type = request.type,
            content = request.content,
            choices = request.choices,
            answer = request.answer,
            explanation = request.explanation,
            topic = request.topic,
            difficulty = request.difficulty,
            timeLimitSec = request.timeLimitSec,
            points = request.points,
        )
        refreshStats(set)
        return question
    }

    /**
     * 문항을 지운다. 남은 문항의 order_no 를 1부터 다시 붙여 빈 번호를 남기지 않는다 —
     * 세션이 순서대로 진행하기 때문에 번호가 촘촘한 편이 다루기 쉽다.
     */
    @Transactional
    fun deleteQuestion(setId: Long, questionId: Long, ownerUserId: Long) {
        val set = getEditableSet(setId, ownerUserId)
        val question = getQuestion(setId, questionId)
        questionRepository.delete(question)
        questionRepository.flush()

        val remaining = questionRepository.findAllBySetIdOrderByOrderNoAsc(setId)
        renumber(remaining)
        set.refreshStats(remaining)
    }

    private fun refreshStats(set: QuestionSet) {
        questionRepository.flush()
        set.refreshStats(questionRepository.findAllBySetIdOrderByOrderNoAsc(set.id))
    }

    /**
     * 문항 순서를 통째로 바꾼다.
     *
     * uk_question_order(set_id, order_no) 때문에 한 번에 바꾸면 중간에 번호가 겹친다.
     * 그래서 먼저 음수로 전부 밀어놓고(1단계) 다시 1..n 을 붙인다(2단계).
     */
    private fun reorder(setId: Long, questionOrder: List<Long>) {
        val questions = questionRepository.findAllBySetIdOrderByOrderNoAsc(setId)
        val byId = questions.associateBy { it.id }

        if (questionOrder.size != questions.size || !byId.keys.containsAll(questionOrder)) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "문항 순서에는 이 세트의 문항 id 를 빠짐없이 한 번씩 넣어야 합니다.",
            )
        }

        questions.forEachIndexed { index, question -> question.changeOrder(-(index + 1)) }
        questionRepository.flush()

        questionOrder.forEachIndexed { index, id -> byId.getValue(id).changeOrder(index + 1) }
        questionRepository.flush()
    }

    /** 삭제 후 1..n 으로 다시 매긴다. 겹침을 피하려고 순서 변경과 같은 2단계 방식을 쓴다. */
    private fun renumber(questions: List<Question>) {
        if (questions.isEmpty()) return
        questions.forEachIndexed { index, question -> question.changeOrder(-(index + 1)) }
        questionRepository.flush()
        questions.forEachIndexed { index, question -> question.changeOrder(index + 1) }
        questionRepository.flush()
    }

    private fun getEditableSet(setId: Long, ownerUserId: Long): QuestionSet {
        val set = questionSetRepository.findByIdAndDeletedAtIsNull(setId)
            ?: throw BusinessException(ErrorCode.QUESTION_SET_NOT_FOUND)
        set.verifyOwner(ownerUserId)
        set.verifyEditable()
        return set
    }

    private fun getQuestion(setId: Long, questionId: Long): Question =
        questionRepository.findByIdAndSetId(questionId, setId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND)
}
