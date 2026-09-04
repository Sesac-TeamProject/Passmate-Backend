package kr.passmate.ai.client

import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionType

/**
 * AI 문항 생성 조건. 화면(W-03)의 "AI로 문제 만들기" 입력이 그대로 들어온다.
 *
 * [topic] · [material] 은 **사용자가 쓴 글**이다 — 프롬프트에 지시문과 섞지 않고
 * 별도 컨텍스트 블록으로 넣는다(프롬프트 인젝션 완화).
 */
data class AiGenerationRequest(
    val topic: String,
    /** 유형별 개수. 예: {MCQ=5, ESSAY=3} */
    val counts: Map<QuestionType, Int>,
    val difficulty: Difficulty,
    /** 강의자료 본문(선택). 있으면 이 범위 안에서 출제한다 */
    val material: String? = null,
    /** 이미 세트에 있는 문항 지문. 같은 문제를 다시 만들지 않게 피할 목록으로 넘긴다 */
    val avoid: List<String> = emptyList(),
) {
    val totalCount: Int get() = counts.values.sum()
}

/**
 * AI 가 만든 문항 한 개. 아직 엔티티가 아니다 —
 * 유형별 필수 조건(보기·정답)은 question 기능의 Question 이 저장 시점에 다시 검증한다.
 */
data class GeneratedQuestion(
    val type: QuestionType,
    val content: String,
    val choices: List<String>?,
    val answer: String,
    val explanation: String?,
    val difficulty: Difficulty,
) {
    /**
     * 유형별 앞뒤가 맞는지 본다. 스키마가 막지 못하는 조건들이다 —
     * "정답이 보기 중 하나" 같은 건 JSON Schema 로 표현할 수 없다.
     *
     * 여기서 걸러야 재시도가 의미를 갖는다. 그냥 저장하면 400(INVALID_QUESTION)이 나가
     * 사용자에게는 "내 입력이 잘못됐다"로 보인다 — 실제로는 AI 가 틀린 것이다.
     */
    fun verifyConsistent() {
        if (content.isBlank()) throw AiCallException("문항 지문이 비어 있습니다.", retryable = true)
        when (type) {
            QuestionType.MCQ -> {
                val options = choices.orEmpty()
                // 프론트가 보기 키를 A·B·C·D 로 고정해 4개가 아니면 화면이 깨진다(웹 버그 리포트 #3).
                // 프롬프트도 "보기 4개"를 요구하므로 검증도 정확히 4개로 맞춘다 — 어긋나면 재시도
                if (options.size != REQUIRED_CHOICES) {
                    throw AiCallException("객관식 보기는 정확히 ${REQUIRED_CHOICES}개여야 합니다.", retryable = true)
                }
                if (answer !in options) {
                    throw AiCallException("객관식 정답이 보기 안에 없습니다.", retryable = true)
                }
            }

            QuestionType.OX ->
                if (answer !in OX_ANSWERS) {
                    throw AiCallException("OX 정답이 O/X 가 아닙니다.", retryable = true)
                }

            QuestionType.ESSAY ->
                if (answer.isBlank()) {
                    throw AiCallException("서술형 모범답안이 비어 있습니다.", retryable = true)
                }
        }
    }

    private companion object {
        const val REQUIRED_CHOICES = 4
        val OX_ANSWERS = setOf("O", "X")
    }
}

/** 생성 결과 + 어떤 모델로 얼마나 걸렸는지. 로그(ai_generation_log)에 그대로 남는다. */
data class AiGenerationResult(
    val questions: List<GeneratedQuestion>,
    val model: String,
    val durationMs: Int,
)
