package kr.passmate.question.service

import kr.passmate.ai.client.GeneratedQuestion
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.question.domain.Question
import kr.passmate.question.domain.QuestionSet
import kr.passmate.question.domain.QuestionSource
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.dto.QuestionSetDuplicateRequest
import kr.passmate.question.dto.QuestionSetUpdateRequest
import kr.passmate.question.repository.QuestionRepository
import kr.passmate.question.repository.QuestionSetRepository
import kr.passmate.room.service.RoomQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionSetService(
    private val questionSetRepository: QuestionSetRepository,
    private val questionRepository: QuestionRepository,
    private val roomQueryService: RoomQueryService,
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

    /**
     * AI 가 만든 문항을 세트 끝에 붙인다. 직접 추가와 달리 여러 개를 한 번에 넣는다.
     *
     * 소유·확정 여부를 **여기서 다시 본다** — AI 호출이 도는 수십 초 사이에
     * 세트가 확정되거나 삭제될 수 있기 때문이다.
     */
    @Transactional
    fun appendGeneratedQuestions(
        setId: Long,
        ownerUserId: Long,
        generated: List<GeneratedQuestion>,
        topic: String,
        timeLimitSec: Int,
        points: Int,
    ): List<Question> {
        val set = getEditableSet(setId, ownerUserId)
        var orderNo = (questionRepository.findTopBySetIdOrderByOrderNoDesc(setId)?.orderNo ?: 0)

        val questions = generated.map {
            questionRepository.save(
                Question(
                    setId = setId,
                    orderNo = ++orderNo,
                    type = it.type,
                    content = it.content,
                    choices = it.choices,
                    answer = it.answer,
                    explanation = it.explanation,
                    topic = topic,
                    difficulty = it.difficulty,
                    timeLimitSec = timeLimitSec,
                    points = points,
                    source = QuestionSource.AI,
                ),
            )
        }
        refreshStats(set)
        return questions
    }

    /** AI 재생성 결과로 문항 하나를 갈아끼운다. 순서·배점·제한시간은 유지된다. */
    @Transactional
    fun replaceWithGeneratedQuestion(
        setId: Long,
        questionId: Long,
        ownerUserId: Long,
        generated: GeneratedQuestion,
    ): Question {
        val set = getEditableSet(setId, ownerUserId)
        val question = getQuestion(setId, questionId)
        question.regenerateByAi(
            type = generated.type,
            content = generated.content,
            choices = generated.choices,
            answer = generated.answer,
            explanation = generated.explanation,
            difficulty = generated.difficulty,
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

    /**
     * 세트를 DRAFT 사본으로 복제한다 (FR-014).
     *
     * 확정된 세트는 불변이라, 지난 세트를 손봐서 다시 쓰려면 이 길밖에 없다.
     * 문항은 정답·해설·주제까지 그대로 옮기고 출처(source)도 보존한다 —
     * AI 가 만든 문항이 복제만으로 "직접 작성"이 되면 검수 이력이 끊긴다.
     *
     * DRAFT 도 복제할 수 있다. 확정 전에 갈래를 나눠 두 벌로 만드는 쪽이 자연스럽다.
     */
    @Transactional
    fun duplicate(setId: Long, ownerUserId: Long, request: QuestionSetDuplicateRequest): QuestionSet {
        val origin = questionSetRepository.findByIdAndDeletedAtIsNull(setId)
            ?: throw BusinessException(ErrorCode.QUESTION_SET_NOT_FOUND)
        origin.verifyOwner(ownerUserId)

        val copy = questionSetRepository.save(
            QuestionSet(
                ownerUserId = ownerUserId,
                title = request.title?.trim()?.takeIf { it.isNotBlank() } ?: copiedTitleOf(origin.title),
                description = origin.description,
                duplicatedFromId = origin.id,
            ),
        )

        val questions = questionRepository.findAllBySetIdOrderByOrderNoAsc(setId).map { origin ->
            Question(
                setId = copy.id,
                orderNo = origin.orderNo,
                type = origin.type,
                content = origin.content,
                choices = origin.choices,
                answer = origin.answer,
                explanation = origin.explanation,
                topic = origin.topic,
                difficulty = origin.difficulty,
                timeLimitSec = origin.timeLimitSec,
                points = origin.points,
                source = origin.source,
            )
        }
        questionRepository.saveAll(questions)
        copy.refreshStats(questions)
        return copy
    }

    /**
     * 세트를 지운다 (FR-014).
     *
     * 물리 삭제는 하지 않는다 — 끝난 방이 `room.question_set_id` 로 이 세트를 참조하고 있어서
     * 지우면 지난 세션의 출제 근거가 사라진다. 목록에서만 감춘다.
     *
     * 아직 안 끝난 방이 물고 있으면 아예 막는다. 감추기만 해도 그 방은 세션을 시작할 수 없게 된다.
     */
    @Transactional
    fun delete(setId: Long, ownerUserId: Long) {
        val set = questionSetRepository.findByIdAndDeletedAtIsNull(setId)
            ?: throw BusinessException(ErrorCode.QUESTION_SET_NOT_FOUND)
        set.verifyOwner(ownerUserId)

        if (roomQueryService.isUsedByActiveRoom(setId)) {
            throw BusinessException(
                ErrorCode.CONFLICT,
                "아직 끝나지 않은 방이 쓰고 있는 세트입니다. 방을 먼저 정리해 주세요.",
            )
        }
        set.delete()
    }

    /** 제목이 100자 상한에 붙어 있어도 접미사가 잘리지 않게 앞쪽을 줄인다. */
    private fun copiedTitleOf(title: String): String {
        val room = TITLE_MAX - COPY_SUFFIX.length
        return title.take(room) + COPY_SUFFIX
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

    companion object {
        private const val TITLE_MAX = 100
        private const val COPY_SUFFIX = " (복사본)"
    }
}
