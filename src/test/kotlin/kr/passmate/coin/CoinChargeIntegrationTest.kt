package kr.passmate.coin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.coin.client.PortOneClient
import kr.passmate.coin.client.PortOnePaymentStatus
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.repository.HostProfileRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 코인 충전 요청·승인 검증 (FR-050 · FR-051 · SC-011).
 *
 * 핵심은 **클라이언트가 보낸 금액을 믿지 않는 것**이다 — 결제 금액은 포트원 조회 API 로
 * 대조하고 나서야 코인이 들어간다.
 */
@AutoConfigureMockMvc
@Transactional
@Import(FakePortOneConfig::class)
class CoinChargeIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostProfileRepository: HostProfileRepository
    @Autowired private lateinit var portOneClient: PortOneClient

    private val fake: FakePortOneClient get() = portOneClient as FakePortOneClient

    private lateinit var token: String
    private lateinit var otherToken: String
    private lateinit var hostToken: String

    @BeforeEach
    fun setUp() {
        fake.reset()
        val userId = userService.loginOrRegister(AuthProvider.GOOGLE, "cc-user", null, "충전자", null).user.id
        token = jwtTokenProvider.issue(userId, false).accessToken

        val otherId = userService.loginOrRegister(AuthProvider.GOOGLE, "cc-other", null, "남", null).user.id
        otherToken = jwtTokenProvider.issue(otherId, false).accessToken

        val hostId = userService.loginOrRegister(AuthProvider.GOOGLE, "cc-host", null, "호스트", null).user.id
        val profile = hostProfileRepository.findByUserId(hostId) ?: HostProfile(userId = hostId)
        profile.applyLevel(3)
        hostProfileRepository.saveAndFlush(profile)
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
    }

    // ─── 충전 요청 ───

    @Test
    fun `충전을 요청하면 결제창에 필요한 값을 준다`() {
        val body = charge(10_000).andExpect(status().isCreated).andReturn().json()

        assertThat(body.get("storeId").asText()).isNotBlank()
        assertThat(body.get("channelKey").asText()).isNotBlank()
        assertThat(body.get("paymentId").asText()).isNotBlank()
        assertThat(body.get("amount").asInt()).isEqualTo(10_000)
        assertThat(body.get("orderName").asText()).contains("10,000")
        assertThat(body.get("status").asText()).isEqualTo("READY")
    }

    @Test
    fun `충전 요청 응답에 API Secret 이 새어 나가지 않는다`() {
        val raw = charge(10_000).andReturn().response.contentAsString

        assertThat(raw).doesNotContain("test-api-secret")
        assertThat(raw).doesNotContain("dGVzdC13ZWJob29rLXNlY3JldC1rZXk=")
    }

    @Test
    fun `요청 단계에서는 코인이 늘지 않는다`() {
        charge(10_000).andExpect(status().isCreated)

        assertThat(balance()).isZero()
    }

    @Test
    fun `충전 금액이 정책 하한보다 낮으면 거절한다`() {
        charge(500).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `충전 금액이 정책 상한보다 높으면 거절한다`() {
        charge(1_000_001).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    // ─── 승인 검증 ───

    @Test
    fun `포트원이 결제 완료라고 하면 코인이 들어온다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        confirm(chargeId).andExpect(status().isOk).andExpect(jsonPath("$.status").value("PAID"))

        assertThat(balance()).isEqualTo(10_000)
    }

    @Test
    fun `포트원이 말하는 금액이 우리 요청과 다르면 코인을 넣지 않는다`() {
        val (chargeId, paymentId) = requested(10_000)
        // 클라이언트가 금액을 조작했거나 다른 결제를 가리키는 경우다
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 100)

        confirm(chargeId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"))

        assertThat(balance()).isZero()
    }

    @Test
    fun `아직 결제되지 않은 건은 확정하지 않는다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.READY, totalAmount = 10_000)

        confirm(chargeId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_COMPLETED"))

        assertThat(balance()).isZero()
    }

    @Test
    fun `두 번 확정해도 코인은 한 번만 들어온다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        confirm(chargeId).andExpect(status().isOk)
        confirm(chargeId).andExpect(status().isOk)

        assertThat(balance()).isEqualTo(10_000)
    }

    @Test
    fun `확정하면 코인 내역에 충전 한 줄이 남는다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)
        confirm(chargeId).andExpect(status().isOk)

        val latest = coins().get("lastTransaction")
        assertThat(latest.get("type").asText()).isEqualTo("CHARGE")
        assertThat(latest.get("amount").asInt()).isEqualTo(10_000)
    }

    @Test
    fun `남의 충전 건은 확정할 수 없다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        confirm(chargeId, otherToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `포트원 조회가 실패하면 502 이고 코인은 그대로다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stubFailure(paymentId)

        confirm(chargeId).andExpect(status().isBadGateway)

        assertThat(balance()).isZero()
    }

    @Test
    fun `없는 충전 건을 확정하면 404`() {
        confirm(999_999L).andExpect(status().isNotFound)
    }

    // ─── 충전 후 참가비 원스텝 ───

    @Test
    fun `roomId 를 함께 보내면 충전 직후 참가비까지 차감한다`() {
        val roomId = paidRoom(1000)
        val (chargeId, paymentId) = requested(10_000, roomId)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        val body = confirm(chargeId).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("entryPayment").get("paymentNo").asText()).startsWith("PM-")
        assertThat(balance()).isEqualTo(9_000)
    }

    @Test
    fun `roomId 가 없으면 참가비 정보는 응답에 빠진다`() {
        val (chargeId, paymentId) = requested(10_000)
        fake.stub(paymentId, PortOnePaymentStatus.PAID, totalAmount = 10_000)

        val body = confirm(chargeId).andReturn().json()

        assertThat(body.has("entryPayment")).isFalse()
    }

    // ─── 헬퍼 ───

    private fun charge(amount: Int, roomId: Long? = null): ResultActions {
        val json = buildString {
            append("""{"amount":$amount,"method":"CARD"""")
            roomId?.let { append(""","roomId":$it""") }
            append("}")
        }
        return mockMvc.perform(
            post("/coins/charges").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(json),
        )
    }

    /** 충전을 요청해 두고 (충전 id, 포트원에 넘길 paymentId) 를 준다. */
    private fun requested(amount: Int, roomId: Long? = null): Pair<Long, String> {
        val body = charge(amount, roomId).andExpect(status().isCreated).andReturn().json()
        return body.get("chargeId").asLong() to body.get("paymentId").asText()
    }

    private fun confirm(chargeId: Long, withToken: String = token): ResultActions = mockMvc.perform(
        post("/coins/charges/{id}/confirm", chargeId).header("Authorization", "Bearer $withToken"),
    )

    private fun paidRoom(fee: Int): Long = mockMvc.perform(
        post("/rooms").header("Authorization", "Bearer $hostToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":"유료 방","type":"PAID","fee":$fee}"""),
    ).andExpect(status().isCreated).andReturn().json().get("id").asLong()

    private fun coins(): JsonNode = mockMvc.perform(
        get("/users/me/coins").header("Authorization", "Bearer $token"),
    ).andReturn().json()

    private fun balance(): Int = coins().get("balance").asInt()

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
