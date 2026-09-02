package kr.passmate.feedback.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.CurrentUser
import kr.passmate.feedback.dto.AnalysisStatus
import kr.passmate.feedback.dto.EssayAnalysisRequestResponse
import kr.passmate.feedback.dto.MyAnswerResponse
import kr.passmate.feedback.service.AnswerFeedbackQueryService
import kr.passmate.feedback.service.EssayAnalysisService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 내 답안·AI 분석. 세션 결과 화면(M-06 · W-07)의 "AI 분석 보기"가 여기로 온다.
 */
@Tag(name = "AI 답변 분석")
@RestController
@RequestMapping("/rooms/{roomId}/session/questions/{questionId}/answers/me")
class AnswerAnalysisController(
    private val answerFeedbackQueryService: AnswerFeedbackQueryService,
    private val essayAnalysisService: EssayAnalysisService,
) {

    @Operation(
        summary = "내 답안·AI 피드백 조회",
        description = "제출 답안·정오·점수와 서술형 AI 피드백. 분석 상태와 이번 달 남은 무료 횟수를 함께 준다.",
    )
    @GetMapping
    fun myAnswer(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
        @PathVariable questionId: Long,
    ): MyAnswerResponse = answerFeedbackQueryService.myAnswer(roomId, questionId, principal)

    /**
     * 202 로 돌려준다 — 여기서 끝나는 게 아니라 접수만 하고 결과는 조회 API 로 확인하기 때문이다.
     * 같은 답안을 다시 눌러도 진행 중·완료 건은 그대로 돌아온다(코인을 두 번 받지 않는다).
     */
    @Operation(
        summary = "내 답안 AI 분석 요청",
        description = "회원 전용. 월 5회 무료, 초과 시 코인 차감(부족하면 402). 접수만 하고 즉시 PENDING 으로 응답한다.",
    )
    @PostMapping("/analysis")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestAnalysis(
        @CurrentUser principal: AuthPrincipal,
        @PathVariable roomId: Long,
        @PathVariable questionId: Long,
    ): EssayAnalysisRequestResponse {
        val state = essayAnalysisService.request(roomId, questionId, principal)
        return EssayAnalysisRequestResponse(
            analysisStatus = AnalysisStatus.of(state.feedback),
            chargedCoins = state.feedback?.chargedCoins ?: 0,
            remainingFreeAnalysis = state.remainingFreeCount,
            analysisCoinCost = state.coinCost,
        )
    }
}
