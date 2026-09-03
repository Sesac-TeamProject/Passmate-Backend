package kr.passmate.coin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 기본 결제 수단 (FR-053, C-02-8).
 *
 * **카드 정보는 저장하지 않는다** — 어떤 수단을 기본으로 고를지만 기억한다.
 * 실제 결제 정보는 전부 포트원이 들고 있다.
 */
@AutoConfigureMockMvc
@Transactional
class PaymentMethodIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val userId = userService.loginOrRegister(AuthProvider.GOOGLE, "pm-user", null, "결제자", null).user.id
        token = jwtTokenProvider.issue(userId, false).accessToken
    }

    @Test
    fun `설정한 적 없으면 기본 결제 수단이 없다`() {
        // non_null 직렬화라 정한 적 없으면 필드 자체가 빠진다
        assertThat(coins().andReturn().json().has("defaultPaymentMethod")).isFalse()
    }

    @Test
    fun `기본 결제 수단을 정하면 내 코인 조회에 함께 나온다`() {
        setMethod("KAKAOPAY").andExpect(status().isOk)

        coins().andExpect(jsonPath("$.defaultPaymentMethod").value("KAKAOPAY"))
    }

    @Test
    fun `설정 응답이 방금 정한 수단을 그대로 돌려준다`() {
        setMethod("TOSSPAY")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultPaymentMethod").value("TOSSPAY"))
    }

    @Test
    fun `다시 정하면 덮어쓴다`() {
        setMethod("CARD").andExpect(status().isOk)
        setMethod("NAVERPAY").andExpect(status().isOk)

        coins().andExpect(jsonPath("$.defaultPaymentMethod").value("NAVERPAY"))
    }

    @Test
    fun `명세에 있는 다섯 수단을 모두 받는다`() {
        listOf("KAKAOPAY", "NAVERPAY", "TOSSPAY", "CARD", "BANK_TRANSFER").forEach {
            setMethod(it).andExpect(status().isOk)
        }
    }

    @Test
    fun `모르는 결제 수단은 거절한다`() {
        setMethod("BITCOIN")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `결제 수단을 비워 보내면 거절한다`() {
        mockMvc.perform(
            put("/users/me/payment-method").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content("""{}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `로그인하지 않으면 401`() {
        mockMvc.perform(
            put("/users/me/payment-method")
                .contentType(MediaType.APPLICATION_JSON).content("""{"method":"CARD"}"""),
        ).andExpect(status().isUnauthorized)
    }

    private fun setMethod(method: String): ResultActions = mockMvc.perform(
        put("/users/me/payment-method").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"method":"$method"}"""),
    )

    private fun coins(): ResultActions =
        mockMvc.perform(get("/users/me/coins").header("Authorization", "Bearer $token"))

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
