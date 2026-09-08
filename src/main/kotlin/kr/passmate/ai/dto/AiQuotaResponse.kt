package kr.passmate.ai.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.ai.service.AiQuota

/**
 * AI 문항 생성 무료 한도 (W-03 "AI로 문제 만들기" 패널의 "n회 남음").
 *
 * 한도를 화면이 복제하지 않게 **서버가 셋 다 준다** — 화면이 상수를 들고 있으면
 * 정책값을 바꾸는 순간 조용히 틀린 숫자를 말하게 된다(웹 QA_BACKLOG B-7).
 */
@Schema(description = "AI 생성 무료 한도와 남은 횟수")
data class AiQuotaResponse(
    @field:Schema(description = "누적 무료 횟수. 생성·재생성을 합쳐 센다(FR-076)")
    val freeLimit: Int,
    @field:Schema(description = "지금까지 성공한 호출 수. 실패는 세지 않는다")
    val usedCount: Int,
    @field:Schema(description = "남은 횟수. 한도를 넘겨도 0 아래로 내려가지 않는다")
    val remainingCount: Int,
) {
    companion object {
        fun from(quota: AiQuota) = AiQuotaResponse(
            freeLimit = quota.freeLimit,
            usedCount = quota.usedCount,
            remainingCount = quota.remainingCount,
        )
    }
}
