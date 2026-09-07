package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import kr.passmate.question.domain.QuestionType

/**
 * 방 단위 문항별 시간(W-02b). 확정 세트는 불변이라 세트를 고치지 않고 이 방에서만 덮어쓴다
 * (웹 버그 리포트 B-18, 2026-09-07 결정 (b)).
 */
@Schema(description = "문항별 시간 설정 요청 — 전체 교체. 본문에 없는 문항은 세트 기본값으로 돌아간다")
data class RoomQuestionTimesRequest(
    @field:NotNull
    @field:Valid
    val times: List<QuestionTimeEntry>,
)

@Schema(description = "문항 하나의 제한시간·자동 넘김")
data class QuestionTimeEntry(
    val questionId: Long,

    @field:Schema(description = "제한시간(초). 5~600")
    @field:Min(5)
    @field:Max(600)
    val timeLimitSec: Int,

    @field:Schema(description = "시간 만료로 마감되면 다음 문항을 자동으로 열지 (W-02b 토글)")
    val autoAdvance: Boolean = false,
)

/** 정답·해설은 싣지 않는다 — 호스트 화면이지만 프로젝터에 그대로 뜰 수 있다. */
@Schema(description = "문항 한 줄 — 세트 기본값과 이 방에서 쓸 값")
data class RoomQuestionTimeView(
    val questionId: Long,
    val orderNo: Int,
    val type: QuestionType,
    val content: String,
    @field:Schema(description = "세트에 적힌 제한시간(초)")
    val defaultTimeLimitSec: Int,
    @field:Schema(description = "이 방에서 쓸 제한시간(초). 덮어쓴 값이 없으면 기본값과 같다")
    val timeLimitSec: Int,
    @field:Schema(description = "이 방에서 덮어쓴 문항인지")
    val overridden: Boolean,
    @field:Schema(description = "시간 만료로 마감되면 다음 문항을 자동으로 열지")
    val autoAdvance: Boolean,
)

@Schema(description = "방 문항별 시간 — 조회·설정 응답")
data class RoomQuestionTimesResponse(
    val roomId: Long,
    val questionSetId: Long,
    @field:Schema(description = "이 방 기준 예상 소요 시간(초, 문항 제한시간 합)")
    val estimatedSeconds: Int,
    val questions: List<RoomQuestionTimeView>,
)
