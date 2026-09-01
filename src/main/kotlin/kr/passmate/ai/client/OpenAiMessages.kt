package kr.passmate.ai.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionType

/**
 * OpenAI Chat Completions 응답에서 우리가 쓰는 부분만 담는다.
 * 응답 스펙이 늘어나도 깨지지 않게 모르는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCompletionResponse(
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Choice(val message: Message? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Message(
        val content: String? = null,
        /** 안전 정책으로 생성을 거부하면 content 대신 이쪽이 채워진다 */
        val refusal: String? = null,
    )
}

/** Structured Outputs 로 받은 JSON 본문. 스키마와 1:1 로 맞춘다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeneratedPayload(
    val questions: List<Item> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val type: QuestionType,
        val content: String,
        val choices: List<String>? = null,
        val answer: String,
        val explanation: String? = null,
        val difficulty: Difficulty,
    ) {
        fun toDomain(): GeneratedQuestion = GeneratedQuestion(
            type = type,
            content = content,
            // 서술형·OX 에 빈 배열이 오면 보기 없음과 같게 다룬다
            choices = choices?.takeIf { it.isNotEmpty() },
            answer = answer,
            explanation = explanation?.takeIf { it.isNotBlank() },
            difficulty = difficulty,
        )
    }
}
