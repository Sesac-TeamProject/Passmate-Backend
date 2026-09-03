package kr.passmate.settlement

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
 * 정산 계좌 등록·조회 (FR-056).
 *
 * 계좌번호는 **응답에 원문이 나오면 안 된다.** 정산 화면은 "어느 계좌인지 알아볼 수 있으면" 충분하다.
 */
@AutoConfigureMockMvc
@Transactional
class SettlementAccountIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val userId = userService
            .loginOrRegister(AuthProvider.GOOGLE, "settle-user", null, "settle-user", null).user.id
        token = jwtTokenProvider.issue(userId, false).accessToken
    }

    @Test
    fun `등록한 적 없으면 미등록으로 답한다`() {
        val body = account().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("registered").asBoolean()).isFalse()
        // non_null 직렬화라 계좌 자체는 필드가 빠진다
        assertThat(body.has("account")).isFalse()
    }

    @Test
    fun `계좌를 등록하면 조회된다`() {
        register("088", "신한은행", "110123456789", "전혜림").andExpect(status().isOk)

        val body = account().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("registered").asBoolean()).isTrue()
        val saved = body.get("account")
        assertThat(saved.get("bankCode").asText()).isEqualTo("088")
        assertThat(saved.get("bankName").asText()).isEqualTo("신한은행")
        assertThat(saved.get("holderName").asText()).isEqualTo("전혜림")
    }

    @Test
    fun `계좌번호는 뒤 네 자리만 보인다`() {
        register("088", "신한은행", "110123456789", "전혜림").andExpect(status().isOk)

        val masked = account().andReturn().json().get("account").get("accountNoMasked").asText()

        assertThat(masked).endsWith("6789")
        assertThat(masked).doesNotContain("110123456789")
        assertThat(masked).contains("*")
    }

    @Test
    fun `응답 어디에도 계좌번호 원문은 없다`() {
        val response = register("088", "신한은행", "110123456789", "전혜림")
            .andExpect(status().isOk).andReturn().response.contentAsString

        assertThat(response).doesNotContain("110123456789")
    }

    @Test
    fun `다시 등록하면 덮어쓴다`() {
        register("088", "신한은행", "110123456789", "전혜림").andExpect(status().isOk)
        register("004", "국민은행", "98765432100", "전혜림").andExpect(status().isOk)

        val saved = account().andReturn().json().get("account")
        assertThat(saved.get("bankName").asText()).isEqualTo("국민은행")
        assertThat(saved.get("accountNoMasked").asText()).endsWith("2100")
    }

    @Test
    fun `등록 직후에는 미검증 상태다`() {
        register("088", "신한은행", "110123456789", "전혜림")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.account.verified").value(false))
    }

    @Test
    fun `은행·계좌번호·예금주는 비울 수 없다`() {
        register("", "신한은행", "110123456789", "전혜림").andExpect(status().isBadRequest)
        register("088", "신한은행", "  ", "전혜림").andExpect(status().isBadRequest)
        register("088", "신한은행", "110123456789", " ").andExpect(status().isBadRequest)
    }

    @Test
    fun `숫자가 아닌 계좌번호는 거절한다`() {
        register("088", "신한은행", "110-1234-5678", "전혜림").andExpect(status().isBadRequest)
    }

    @Test
    fun `로그인하지 않으면 계좌를 볼 수 없다`() {
        mockMvc.perform(get("/users/me/settlement-account")).andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    private fun account(): ResultActions =
        mockMvc.perform(get("/users/me/settlement-account").header(AUTH, "Bearer $token"))

    private fun register(bankCode: String, bankName: String, accountNo: String, holder: String): ResultActions =
        mockMvc.perform(
            put("/users/me/settlement-account")
                .header(AUTH, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "bankCode" to bankCode,
                            "bankName" to bankName,
                            "accountNo" to accountNo,
                            "holderName" to holder,
                        ),
                    ),
                ),
        )

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
