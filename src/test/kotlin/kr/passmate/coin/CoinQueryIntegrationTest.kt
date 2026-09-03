package kr.passmate.coin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransactionType
import kr.passmate.coin.service.CoinService
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 내 코인 조회·내역 (FR-050 · FR-053).
 *
 * 내역은 **원장(coin_transaction)** 을 그대로 보여준다. 방 제목·결제 번호는 차감 시점에
 * memo 로 박아 둔 값을 쓴다 — 나중에 방 제목이 바뀌어도 영수증은 그때 그대로여야 한다.
 */
@AutoConfigureMockMvc
@Transactional
class CoinQueryIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var coinService: CoinService

    private var userId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        userId = userService.loginOrRegister(AuthProvider.GOOGLE, "coin-user", null, "코인이", null).user.id
        token = jwtTokenProvider.issue(userId, false).accessToken
    }

    @Test
    fun `지갑이 비어 있으면 잔액 0 으로 답한다`() {
        val body = coins().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("balance").asInt()).isZero()
        // non_null 직렬화라 최근 내역이 없으면 필드 자체가 빠진다
        assertThat(body.has("lastTransaction")).isFalse()
    }

    @Test
    fun `환급으로 코인이 들어오면 잔액에 반영된다`() {
        coinService.refund(userId, 500, CoinRefType.ENTRY_PAYMENT, 1L, "테스트 적립")

        val body = coins().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("balance").asInt()).isEqualTo(500)
    }

    @Test
    fun `내 코인 조회는 가장 최근 내역 한 건을 함께 준다`() {
        coinService.refund(userId, 500, CoinRefType.ENTRY_PAYMENT, 1L, "먼저 들어온 건")
        coinService.deduct(userId, 100, CoinTransactionType.AI_ANALYSIS, CoinRefType.AI_FEEDBACK, 2L, "나중 건")

        val last = coins().andExpect(status().isOk).andReturn().json().get("lastTransaction")

        assertThat(last.get("description").asText()).isEqualTo("나중 건")
        assertThat(last.get("amount").asInt()).isEqualTo(-100)
    }

    @Test
    fun `내역은 최근 순으로 나오고 건별 잔액이 붙는다`() {
        coinService.refund(userId, 500, CoinRefType.ENTRY_PAYMENT, 1L, "충전분")
        coinService.deduct(userId, 100, CoinTransactionType.AI_ANALYSIS, CoinRefType.AI_FEEDBACK, 2L, "분석 차감")

        val content = transactions().andExpect(status().isOk).andReturn().json().get("content")

        assertThat(content).hasSize(2)
        assertThat(content[0].get("description").asText()).isEqualTo("분석 차감")
        assertThat(content[0].get("balanceAfter").asInt()).isEqualTo(400)
        assertThat(content[1].get("description").asText()).isEqualTo("충전분")
        assertThat(content[1].get("balanceAfter").asInt()).isEqualTo(500)
    }

    @Test
    fun `차감은 음수로 충전은 양수로 나온다`() {
        coinService.refund(userId, 500, CoinRefType.ENTRY_PAYMENT, 1L, "충전분")
        coinService.deduct(userId, 100, CoinTransactionType.AI_ANALYSIS, CoinRefType.AI_FEEDBACK, 2L, "분석 차감")

        val content = transactions().andReturn().json().get("content")

        assertThat(content[0].get("amount").asInt()).isEqualTo(-100)
        assertThat(content[1].get("amount").asInt()).isEqualTo(500)
    }

    @Test
    fun `남의 내역은 섞여 나오지 않는다`() {
        val otherId = userService.loginOrRegister(AuthProvider.GOOGLE, "other", null, "남", null).user.id
        coinService.refund(otherId, 900, CoinRefType.ENTRY_PAYMENT, 3L, "남의 건")
        coinService.refund(userId, 500, CoinRefType.ENTRY_PAYMENT, 1L, "내 건")

        val content = transactions().andReturn().json().get("content")

        assertThat(content).hasSize(1)
        assertThat(content[0].get("description").asText()).isEqualTo("내 건")
    }

    @Test
    fun `페이지 크기를 넘기면 다음 페이지가 있다고 알린다`() {
        repeat(3) { coinService.refund(userId, 100, CoinRefType.ENTRY_PAYMENT, it + 1L, "건 $it") }

        val body = transactions(size = 2).andReturn().json()

        assertThat(body.get("content")).hasSize(2)
        assertThat(body.get("totalElements").asLong()).isEqualTo(3)
        assertThat(body.get("hasNext").asBoolean()).isTrue()
    }

    @Test
    fun `로그인하지 않으면 401`() {
        mockMvc.perform(get("/users/me/coins")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/users/me/coins/transactions")).andExpect(status().isUnauthorized)
    }

    private fun coins(): ResultActions =
        mockMvc.perform(get("/users/me/coins").header("Authorization", "Bearer $token"))

    private fun transactions(size: Int = 20): ResultActions = mockMvc.perform(
        get("/users/me/coins/transactions")
            .param("size", size.toString())
            .header("Authorization", "Bearer $token"),
    )

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
