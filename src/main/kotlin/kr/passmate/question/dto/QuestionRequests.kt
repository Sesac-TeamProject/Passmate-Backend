package kr.passmate.question.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionType

@Schema(description = "문제 세트 생성 요청 — 빈 세트를 만들고 이후 문항을 채운다")
data class QuestionSetCreateRequest(
    @field:NotBlank(message = "세트 제목은 필수입니다.")
    @field:Size(max = 100)
    val title: String,

    @field:Size(max = 500)
    val description: String? = null,
)

@Schema(description = "문제 세트 복제 요청 — 제목만 골라서 바꿀 수 있다")
data class QuestionSetDuplicateRequest(
    @field:Schema(description = "사본 제목. 비우면 원본 제목 뒤에 \"(복사본)\" 이 붙는다")
    @field:Size(max = 100)
    val title: String? = null,
)

@Schema(description = "문제 세트 수정 요청 — 확정 전에만 가능")
data class QuestionSetUpdateRequest(
    @field:NotBlank(message = "세트 제목은 필수입니다.")
    @field:Size(max = 100)
    val title: String,

    @field:Size(max = 500)
    val description: String? = null,

    @field:Schema(
        description = "문항 순서를 바꿀 때 원하는 순서대로 문항 id 를 전부 보낸다. " +
            "비우면 순서는 그대로 둔다",
    )
    val questionOrder: List<Long>? = null,
)

@Schema(description = "문항 추가·수정 요청")
data class QuestionRequest(
    @field:NotNull(message = "문항 유형은 필수입니다.")
    val type: QuestionType,

    @field:NotBlank(message = "문항 지문은 필수입니다.")
    val content: String,

    @field:Schema(description = "객관식 보기. MCQ 는 2개 이상 필요하다")
    val choices: List<String>? = null,

    @field:Schema(description = "정답. MCQ 는 보기 중 하나, OX 는 O 또는 X, 서술형은 모범답안")
    @field:Size(max = 500)
    val answer: String? = null,

    val explanation: String? = null,

    @field:Size(max = 100)
    val topic: String? = null,

    val difficulty: Difficulty? = null,

    @field:Schema(description = "제한시간(초). 5~600")
    val timeLimitSec: Int = DEFAULT_TIME_LIMIT_SEC,

    @field:Schema(description = "배점. 1~1000")
    val points: Int = DEFAULT_POINTS,
) {
    companion object {
        const val DEFAULT_TIME_LIMIT_SEC = 30
        const val DEFAULT_POINTS = 100
    }
}
