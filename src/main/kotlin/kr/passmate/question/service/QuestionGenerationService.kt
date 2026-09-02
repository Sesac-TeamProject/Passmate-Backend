package kr.passmate.question.service

import kr.passmate.ai.client.AiGenerationRequest
import kr.passmate.ai.service.AiQuestionService
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.Question
import kr.passmate.question.dto.AiGenerateRequest
import org.springframework.stereotype.Service

/**
 * AI 문항 생성의 순서를 맡는다: **검증 → (트랜잭션 밖) AI 호출 → 저장**.
 *
 * `@Transactional` 을 붙이지 않는 게 핵심이다. OpenAI 응답은 수십 초가 걸릴 수 있어
 * 그동안 DB 커넥션과 락을 쥐고 있으면 진행 중인 세션까지 느려진다.
 * 그래서 읽기·쓰기는 각각 자기 트랜잭션에서 짧게 끝내고, 그 사이에 외부 호출을 둔다.
 */
@Service
class QuestionGenerationService(
    private val aiQuestionService: AiQuestionService,
    private val questionSetQueryService: QuestionSetQueryService,
    private val questionSetService: QuestionSetService,
) {

    /** 조건대로 문항을 만들어 세트 끝에 붙인다. 무료 한도를 세는 경로다. */
    fun generate(setId: Long, ownerUserId: Long, request: AiGenerateRequest): List<Question> {
        // 1) 짧은 읽기 트랜잭션 — 남의 세트·확정된 세트면 호출 전에 막는다(= 돈을 쓰지 않는다)
        val (_, existing) = questionSetQueryService.getEditableDetail(setId, ownerUserId)

        // 2) 트랜잭션 밖 외부 호출
        val generated = aiQuestionService.generateForSet(
            userId = ownerUserId,
            setId = setId,
            request = AiGenerationRequest(
                topic = request.topic,
                counts = request.counts.filterValues { it > 0 },
                difficulty = request.difficulty,
                material = request.material,
                avoid = existing.map { it.content },
            ),
        )

        // 3) 짧은 쓰기 트랜잭션
        return questionSetService.appendGeneratedQuestions(
            setId = setId,
            ownerUserId = ownerUserId,
            generated = generated,
            topic = request.topic,
            timeLimitSec = request.timeLimitSec,
            points = request.points,
        )
    }

    /**
     * 문항 하나를 **같은 조건으로** 다시 만들어 교체한다.
     * 조건은 요청 본문이 아니라 기존 문항에서 읽는다 — 그게 "같은 조건"의 뜻이다.
     */
    fun regenerate(setId: Long, questionId: Long, ownerUserId: Long): Question {
        val (set, existing) = questionSetQueryService.getEditableDetail(setId, ownerUserId)
        val target = existing.find { it.id == questionId }
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_FOUND)

        val generated = aiQuestionService.regenerate(
            userId = ownerUserId,
            setId = setId,
            request = AiGenerationRequest(
                topic = target.topic ?: set.title,
                counts = mapOf(target.type to 1),
                difficulty = target.difficulty ?: Difficulty.NORMAL,
                // 자기 자신을 포함한 나머지 문항을 피할 목록으로 준다 — 같은 문제가 다시 나오면 재생성이 무의미하다
                avoid = existing.map { it.content },
            ),
        )

        return questionSetService.replaceWithGeneratedQuestion(setId, questionId, ownerUserId, generated)
    }
}
