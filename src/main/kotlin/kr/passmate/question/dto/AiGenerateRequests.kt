package kr.passmate.question.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionType

/**
 * AI 문항 생성 요청 (W-03 "AI로 문제 만들기").
 * 생성된 문항은 세트 **끝에** 붙는다 — 직접 쓴 문항과 섞어 구성할 수 있다.
 */
@Schema(description = "AI 문항 생성 요청 — 주제·유형별 개수·난이도")
data class AiGenerateRequest(
    @field:Schema(description = "출제 주제", example = "자료구조 - 스택과 큐")
    @field:NotBlank(message = "주제는 필수입니다.")
    @field:Size(max = 100)
    val topic: String,

    @field:Schema(description = "유형별 문항 수", example = """{"MCQ": 5, "ESSAY": 3}""")
    @field:NotEmpty(message = "유형별 문항 수는 필수입니다.")
    val counts: Map<QuestionType, Int>,

    val difficulty: Difficulty = Difficulty.NORMAL,

    @field:Schema(description = "강의자료 본문(선택). 넣으면 이 범위 안에서 출제한다")
    @field:Size(max = MATERIAL_MAX_LENGTH, message = "강의자료는 ${MATERIAL_MAX_LENGTH}자를 넘을 수 없습니다.")
    val material: String? = null,

    @field:Schema(description = "생성될 문항의 제한시간(초). 5~600")
    @field:Min(5)
    @field:Max(600)
    val timeLimitSec: Int = QuestionRequest.DEFAULT_TIME_LIMIT_SEC,

    @field:Schema(description = "생성될 문항의 배점. 1~1000")
    @field:Min(1)
    @field:Max(1000)
    val points: Int = QuestionRequest.DEFAULT_POINTS,
) {
    val totalCount: Int get() = counts.values.sum()

    /** 한 번에 만들 수 있는 양을 막는다 — 30초 SLA 와 호출 비용이 걸린 값이다. */
    @get:AssertTrue(message = "한 번에 만들 수 있는 문항은 1~${MAX_GENERATE_COUNT}개입니다.")
    @get:Schema(hidden = true)
    val isTotalCountValid: Boolean
        get() = counts.values.all { it >= 0 } && totalCount in 1..MAX_GENERATE_COUNT

    companion object {
        const val MAX_GENERATE_COUNT = 20
        const val MATERIAL_MAX_LENGTH = 5000
    }
}
