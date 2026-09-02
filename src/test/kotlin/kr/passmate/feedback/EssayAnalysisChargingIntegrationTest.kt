package kr.passmate.feedback

import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransactionType
import kr.passmate.feedback.domain.AiFeedbackStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 무료 한도를 이미 다 쓴 상태(한도 0). 차감·부족·환급을 여기서 본다.
 */
@TestPropertySource(properties = ["passmate.policy.essay-analysis-free-limit=0"])
class EssayAnalysisChargingIntegrationTest : EssayAnalysisTestBase() {

    @Test
    fun `무료 한도를 넘기면 코인을 차감하고 원장에 남긴다`() {
        chargeWallet(1_000)

        requestAnalysis(studentToken, essayId)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.chargedCoins").value(policy.essayAnalysisCoinCost))
            .andExpect(jsonPath("$.remainingFreeAnalysis").value(0))

        assertThat(balance()).isEqualTo(1_000 - policy.essayAnalysisCoinCost)

        val entry = ledger().single()
        assertThat(entry.type).isEqualTo(CoinTransactionType.AI_ANALYSIS)
        assertThat(entry.amount).isEqualTo(-policy.essayAnalysisCoinCost)
        // 원장 한 줄만 봐도 그 시점 잔액을 알 수 있어야 한다
        assertThat(entry.balanceAfter).isEqualTo(balance())
        assertThat(entry.refType).isEqualTo(CoinRefType.AI_FEEDBACK)
        assertThat(entry.refId).isEqualTo(feedbackId())
    }

    @Test
    fun `코인이 모자라면 402 로 막는다`() {
        requestAnalysis(studentToken, essayId)
            .andExpect(status().isPaymentRequired)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_COINS"))
    }

    @Test
    fun `버튼을 두 번 눌러도 코인을 두 번 받지 않는다`() {
        chargeWallet(policy.essayAnalysisCoinCost)

        requestAnalysis(studentToken, essayId)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.chargedCoins").value(policy.essayAnalysisCoinCost))

        // 진행 중인 건은 그대로 돌려준다 — 두 번째 차감이 없으니 402 도 나지 않는다
        requestAnalysis(studentToken, essayId)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.analysisStatus").value("PENDING"))

        assertThat(balance()).isZero()
        assertThat(ledger()).hasSize(1)
    }

    @Test
    fun `분석이 실패하면 차감분을 돌려주고 다시 요청할 수 있다`() {
        chargeWallet(policy.essayAnalysisCoinCost)
        requestAnalysis(studentToken, essayId).andExpect(status().isAccepted)

        val id = feedbackId()
        essayAnalysisService.fail(id, "테스트용 실패")

        val failed = aiFeedbackRepository.findById(id).orElseThrow()
        assertThat(failed.status).isEqualTo(AiFeedbackStatus.FAILED)
        // 부담이 사라졌으므로 0 으로 되돌아간다
        assertThat(failed.chargedCoins).isZero()
        assertThat(balance()).isEqualTo(policy.essayAnalysisCoinCost)
        assertThat(ledger().map { it.type })
            .containsExactly(CoinTransactionType.REFUND, CoinTransactionType.AI_ANALYSIS)

        // 실패한 건은 같은 줄을 되써서 다시 걸 수 있다
        requestAnalysis(studentToken, essayId)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.analysisStatus").value("PENDING"))
        assertThat(feedbackId()).isEqualTo(id)
        assertThat(balance()).isZero()
    }

    @Test
    fun `같은 실패 건을 두 번 마감해도 환급은 한 번뿐이다`() {
        chargeWallet(policy.essayAnalysisCoinCost)
        requestAnalysis(studentToken, essayId).andExpect(status().isAccepted)

        val id = feedbackId()
        essayAnalysisService.fail(id, "첫 실패")
        essayAnalysisService.fail(id, "중복 콜백")

        assertThat(balance()).isEqualTo(policy.essayAnalysisCoinCost)
        assertThat(ledger().count { it.type == CoinTransactionType.REFUND }).isEqualTo(1)
    }
}
