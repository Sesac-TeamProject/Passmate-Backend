package kr.passmate.question.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kr.passmate.common.dto.PageResponse
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import kr.passmate.question.domain.QuestionSetStatus
import kr.passmate.question.dto.AiGenerateRequest
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionResponse
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.dto.QuestionSetDetailResponse
import kr.passmate.question.dto.QuestionSetSummaryResponse
import kr.passmate.question.dto.QuestionSetUpdateRequest
import kr.passmate.question.service.QuestionGenerationService
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.question.service.QuestionSetService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "문제 세트")
@RestController
@RequestMapping("/question-sets")
class QuestionSetController(
    private val questionSetService: QuestionSetService,
    private val questionSetQueryService: QuestionSetQueryService,
    private val questionGenerationService: QuestionGenerationService,
) {

    @Operation(
        summary = "내 문제 세트 목록 조회",
        description = "방 만들기의 세트 선택에도 쓴다. status=CONFIRMED 로 걸러 출제 가능한 것만 볼 수 있다.",
    )
    @GetMapping
    fun list(
        @CurrentUser principal: UserPrincipal,
        @RequestParam(required = false) status: QuestionSetStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): PageResponse<QuestionSetSummaryResponse> =
        PageResponse.from(
            questionSetQueryService.list(principal.userId, status, pageable),
            QuestionSetSummaryResponse::from,
        )

    @Operation(summary = "문제 세트 직접 생성", description = "빈 세트를 만든다. 문항은 이후 추가하거나 AI 로 생성한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: QuestionSetCreateRequest,
    ): QuestionSetSummaryResponse =
        QuestionSetSummaryResponse.from(questionSetService.create(principal.userId, request))

    @Operation(summary = "문제 세트 상세 조회", description = "문항 목록(정답·해설 포함). 소유자만 볼 수 있다.")
    @GetMapping("/{setId}")
    fun get(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
    ): QuestionSetDetailResponse {
        val (set, questions) = questionSetQueryService.getDetail(setId, principal.userId)
        return QuestionSetDetailResponse.of(set, questions)
    }

    @Operation(summary = "문제 세트 수정", description = "제목·설명·문항 순서를 바꾼다. 확정 전에만 가능하다.")
    @PutMapping("/{setId}")
    fun update(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
        @Valid @RequestBody request: QuestionSetUpdateRequest,
    ): QuestionSetSummaryResponse =
        QuestionSetSummaryResponse.from(questionSetService.update(setId, principal.userId, request))

    @Operation(
        summary = "문제 세트 확정",
        description = "확정하면 불변이 되고, 확정된 세트만 세션에 출제할 수 있다.",
    )
    @PostMapping("/{setId}/confirm")
    fun confirm(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
    ): QuestionSetSummaryResponse =
        QuestionSetSummaryResponse.from(questionSetService.confirm(setId, principal.userId))

    @Operation(summary = "문항 추가", description = "직접 작성한 객관식·OX·서술형 문항을 세트 끝에 붙인다.")
    @PostMapping("/{setId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addQuestion(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
        @Valid @RequestBody request: QuestionRequest,
    ): QuestionResponse =
        QuestionResponse.from(questionSetService.addQuestion(setId, principal.userId, request))

    @Operation(
        summary = "AI 문항 생성(세트에 추가)",
        description = "주제·유형별 개수·난이도로 문항을 만들어 세트 끝에 붙인다. 확정 전에만 가능하다. " +
            "무료 횟수를 다 쓰면 429, AI 실패는 502 로 응답하고 무료 횟수는 깎지 않는다.",
    )
    @PostMapping("/{setId}/questions/generate")
    @ResponseStatus(HttpStatus.CREATED)
    fun generateQuestions(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
        @Valid @RequestBody request: AiGenerateRequest,
    ): List<QuestionResponse> =
        questionGenerationService.generate(setId, principal.userId, request).map(QuestionResponse::from)

    @Operation(
        summary = "문항 AI 재생성",
        description = "그 문항만 같은 조건(유형·주제·난이도·배점·제한시간)으로 다시 만들어 교체한다. " +
            "재생성은 무료 횟수를 깎지 않는다.",
    )
    @PostMapping("/{setId}/questions/{questionId}/regenerate")
    fun regenerateQuestion(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
        @PathVariable questionId: Long,
    ): QuestionResponse =
        QuestionResponse.from(questionGenerationService.regenerate(setId, questionId, principal.userId))

    @Operation(summary = "문항 수정", description = "확정 전에만 가능하다.")
    @PutMapping("/{setId}/questions/{questionId}")
    fun updateQuestion(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
        @PathVariable questionId: Long,
        @Valid @RequestBody request: QuestionRequest,
    ): QuestionResponse =
        QuestionResponse.from(
            questionSetService.updateQuestion(setId, questionId, principal.userId, request),
        )

    @Operation(summary = "문항 삭제", description = "확정 전에만 가능하다. 남은 문항의 순서를 1부터 다시 매긴다.")
    @DeleteMapping("/{setId}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteQuestion(
        @CurrentUser principal: UserPrincipal,
        @PathVariable setId: Long,
        @PathVariable questionId: Long,
    ) {
        questionSetService.deleteQuestion(setId, questionId, principal.userId)
    }
}
