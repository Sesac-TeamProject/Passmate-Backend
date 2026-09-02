package kr.passmate.feedback

import kr.passmate.ai.client.EssayAnalysisResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 무료 한도가 남아 있는 상태. OpenAI 는 Fake 라 **실제 호출이 나가지 않는다**.
 *
 * 실제 호출은 AFTER_COMMIT 에 걸려 있어 롤백되는 테스트 트랜잭션 안에서는 뜨지 않는다.
 * 그래서 접수 경로는 HTTP 로, 완료 처리는 Service 를 직접 불러 확인한다.
 */
class EssayAnalysisIntegrationTest : EssayAnalysisTestBase() {

    @Test
    fun `무료 한도 안이면 코인을 받지 않고 PENDING 으로 접수한다`() {
        requestAnalysis(studentToken, essayId)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.analysisStatus").value("PENDING"))
            .andExpect(jsonPath("$.chargedCoins").value(0))
            .andExpect(jsonPath("$.remainingFreeAnalysis").value(policy.essayAnalysisFreeLimit - 1))

        // 무료분은 원장에 아무것도 남기지 않는다
        assertThat(ledger()).isEmpty()
    }

    @Test
    fun `게스트는 분석을 요청할 수 없다`() {
        requestAnalysis(guestToken, essayId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("GUEST_NOT_ALLOWED"))
    }

    @Test
    fun `서술형이 아니면 분석하지 않는다`() {
        requestAnalysis(studentToken, mcqId)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `분석을 요청하지 않았으면 상태는 미요청이고 점수는 그대로 보인다`() {
        myAnswer(studentToken, essayId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.analysisStatus").value("NOT_REQUESTED"))
            .andExpect(jsonPath("$.analysis").doesNotExist())
            .andExpect(jsonPath("$.remainingFreeAnalysis").value(policy.essayAnalysisFreeLimit))
            .andExpect(jsonPath("$.submitted").value(SUBMITTED))
            .andExpect(jsonPath("$.finalScore").isNumber)
    }

    @Test
    fun `분석이 끝나면 조회에서 핵심·부족·개선을 준다`() {
        requestAnalysis(studentToken, essayId).andExpect(status().isAccepted)

        essayAnalysisService.complete(
            feedbackId(),
            EssayAnalysisResult(
                keyPoints = listOf("연결지향을 짚었습니다"),
                missingPoints = listOf("흐름 제어가 빠졌습니다"),
                suggestions = listOf("3-way handshake 를 덧붙여 보세요"),
                summary = "방향은 맞습니다.",
                model = "fake-analysis",
                durationMs = 12,
            ),
        )

        myAnswer(studentToken, essayId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.analysisStatus").value("DONE"))
            .andExpect(jsonPath("$.analysis.keyPoints[0]").value("연결지향을 짚었습니다"))
            .andExpect(jsonPath("$.analysis.missingPoints[0]").value("흐름 제어가 빠졌습니다"))
            .andExpect(jsonPath("$.analysis.suggestions[0]").value("3-way handshake 를 덧붙여 보세요"))
            .andExpect(jsonPath("$.analysis.summary").value("방향은 맞습니다."))
    }

    @Test
    fun `분석이 실패해도 정오·점수는 그대로 볼 수 있다`() {
        requestAnalysis(studentToken, essayId).andExpect(status().isAccepted)
        essayAnalysisService.fail(feedbackId(), "테스트용 실패")

        myAnswer(studentToken, essayId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.analysisStatus").value("FAILED"))
            .andExpect(jsonPath("$.analysis").doesNotExist())
            .andExpect(jsonPath("$.finalScore").isNumber)
    }

    @Test
    fun `문항이 마감된 뒤에만 모범답안을 내보낸다`() {
        myAnswer(studentToken, essayId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").doesNotExist())

        mockMvc.perform(post("/rooms/$roomId/session/current/end").header(AUTH, bearer(hostToken)))
            .andExpect(status().isNoContent)

        myAnswer(studentToken, essayId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value(MODEL_ANSWER))
    }

    @Test
    fun `키가 설정되지 않았으면 코인을 받기 전에 502 로 막는다`() {
        fake.isConfigured = false

        requestAnalysis(studentToken, essayId)
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("AI_ANALYSIS_FAILED"))

        assertThat(ledger()).isEmpty()
    }
}
