package kr.passmate.coin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.coin.client.PortOneClient
import kr.passmate.coin.client.PortOnePaymentStatus
import kr.passmate.coin.domain.CoinChargeStatus
import kr.passmate.coin.repository.CoinChargeRepository
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.support.FakePortOneClient
import kr.passmate.support.FakePortOneConfig
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 포트원 웹훅 수신 (FR-051).
 *
 * 클라이언트의 승인 호출이 끊겨도 **코인 적립을 보장**하는 경로다.
 * 주소가 공개돼 있으므로 서명 검증이 유일한 방벽이다.
 */
@AutoConfigureMockMvc
@Transactional
@Import(FakePortOneConfig::class)
class PortOneWebhookIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var portOneClient: PortOneClient
    @Autowired private lateinit var coinChargeRepository: CoinChargeRepository

    private val fake: FakePortOneClient get() = portOneClient as FakePortOneClient

    /** application-test.yml 의 값과 같아야 한다 */
    private val webhookSecret = "whsec_dGVzdC13ZWJob29rLXNlY3JldC1rZXk="

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        fake.reset()
        val userId = userService.loginOrRegister(AuthProvider.GOOGLE, "wh-user", null, "충전자", null).user.id
        token = jwtTokenProvider.issue(userId, false).accessToken
    }

    @Test
    fun `서명이 맞고 결제가 완료됐으면 코인이 들어온다`() {
        val (_, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        webhook(paidBody(paymentId)).andExpect(status().isOk)

        assertThat(balance()).isEqualTo(10_000)
    }

    @Test
    fun `서명이 없으면 거부하고 코인은 그대로다`() {
        val (_, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        mockMvc.perform(
            post("/webhooks/portone").contentType(MediaType.APPLICATION_JSON).content(paidBody(paymentId)),
        ).andExpect(status().isUnauthorized)

        assertThat(balance()).isZero()
    }

    @Test
    fun `위조된 서명은 거부한다`() {
        val (_, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)
        val body = paidBody(paymentId)

        mockMvc.perform(
            post("/webhooks/portone")
                .header("webhook-id", "msg_1")
                .header("webhook-timestamp", Instant.now().epochSecond.toString())
                .header("webhook-signature", "v1,bm90LWEtcmVhbC1zaWduYXR1cmU=")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isUnauthorized)

        assertThat(balance()).isZero()
    }

    @Test
    fun `승인 호출 없이 웹훅만 와도 코인이 적립된다`() {
        // 사용자가 결제 직후 창을 닫아 confirm 이 오지 않은 상황이다
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        webhook(paidBody(paymentId)).andExpect(status().isOk)

        assertThat(coinChargeRepository.findById(chargeId).get().status).isEqualTo(CoinChargeStatus.PAID)
        assertThat(balance()).isEqualTo(10_000)
    }

    @Test
    fun `승인 호출과 웹훅이 둘 다 와도 코인은 한 번만 들어온다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        mockMvc.perform(
            post("/coins/charges/{id}/confirm", chargeId).header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk)
        webhook(paidBody(paymentId)).andExpect(status().isOk)

        assertThat(balance()).isEqualTo(10_000)
    }

    @Test
    fun `같은 웹훅이 재시도로 여러 번 와도 코인은 한 번만 들어온다`() {
        val (_, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        repeat(3) { webhook(paidBody(paymentId)).andExpect(status().isOk) }

        assertThat(balance()).isEqualTo(10_000)
    }

    @Test
    fun `모르는 결제 건이면 조용히 넘어간다`() {
        // 우리 DB 에 없는 결제다. 400 을 주면 포트원이 5번까지 재시도한다
        webhook(paidBody("pm-charge-somebody-else")).andExpect(status().isOk)
    }

    @Test
    fun `포트원이 말하는 금액이 다르면 적립하지 않는다`() {
        val (_, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 100)

        webhook(paidBody(paymentId)).andExpect(status().isOk)

        assertThat(balance()).isZero()
    }

    @Test
    fun `결제 실패 웹훅이면 충전 건이 FAILED 가 된다`() {
        val (chargeId, paymentId) = requested(10_000)

        webhook(bodyOf("Transaction.Failed", paymentId)).andExpect(status().isOk)

        assertThat(coinChargeRepository.findById(chargeId).get().status).isEqualTo(CoinChargeStatus.FAILED)
        assertThat(balance()).isZero()
    }

    @Test
    fun `결제 취소 웹훅이면 충전 건이 CANCELED 가 된다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)
        webhook(paidBody(paymentId)).andExpect(status().isOk)

        webhook(bodyOf("Transaction.Cancelled", paymentId)).andExpect(status().isOk)

        assertThat(coinChargeRepository.findById(chargeId).get().status).isEqualTo(CoinChargeStatus.CANCELED)
    }

    // ─── 헬퍼 ───

    private fun requested(amount: Int): Pair<Long, String> {
        val body = mockMvc.perform(
            post("/coins/charges").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":$amount,"method":"CARD"}"""),
        ).andExpect(status().isCreated).andReturn().json()
        return body.get("chargeId").asLong() to body.get("paymentId").asText()
    }

    private fun paidBody(paymentId: String) = bodyOf("Transaction.Paid", paymentId)

    private fun bodyOf(type: String, paymentId: String) =
        """{"type":"$type","timestamp":"2026-09-03T00:00:00Z","data":{"paymentId":"$paymentId","storeId":"store-test"}}"""

    private fun webhook(body: String): ResultActions {
        val id = "msg_${System.nanoTime()}"
        val epoch = Instant.now().epochSecond
        val key = Base64.getDecoder().decode(webhookSecret.removePrefix("whsec_"))
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val signature = Base64.getEncoder().encodeToString(mac.doFinal("$id.$epoch.$body".toByteArray()))

        return mockMvc.perform(
            post("/webhooks/portone")
                .header("webhook-id", id)
                .header("webhook-timestamp", epoch.toString())
                .header("webhook-signature", "v1,$signature")
                .contentType(MediaType.APPLICATION_JSON).content(body),
        )
    }

    private fun balance(): Int = mockMvc.perform(
        get("/users/me/coins").header("Authorization", "Bearer $token"),
    ).andReturn().json().get("balance").asInt()

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
