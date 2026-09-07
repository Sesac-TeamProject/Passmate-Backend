package kr.passmate.ai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.passmate.ai.dto.AiQuotaResponse
import kr.passmate.ai.service.AiQuestionService
import kr.passmate.common.security.CurrentUser
import kr.passmate.common.security.UserPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * AI 생성 무료 한도 조회. 에디터가 생성 버튼 옆에 "n회 남음"을 띄우는 데 쓴다.
 *
 * 코인 잔액(`/users/me/coins`)과 같은 자리에 둔다 — 둘 다 "내 계정에 남은 것"이다.
 */
@Tag(name = "AI 문제 생성")
@RestController
class AiQuotaController(
    private val aiQuestionService: AiQuestionService,
) {

    @Operation(
        summary = "AI 생성 무료 한도 조회",
        description = "누적 무료 횟수·사용한 횟수·남은 횟수. 생성과 재생성이 한도를 공유하고, " +
            "실패한 호출은 세지 않는다. 회원 전용.",
    )
    @GetMapping("/users/me/ai-quota")
    fun myQuota(@CurrentUser principal: UserPrincipal): AiQuotaResponse =
        AiQuotaResponse.from(aiQuestionService.freeQuota(principal.userId))
}
