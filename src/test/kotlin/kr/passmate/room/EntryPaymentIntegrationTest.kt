package kr.passmate.room

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.service.CoinService
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.repository.HostProfileRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 참가비 코인 차감과 입장 게이트 (FR-050 · FR-051).
 *
 * 게이트는 **서버에서만** 닫힌다 — 결제하지 않고 입장 API 를 바로 부르면 막혀야 한다.
 */
@AutoConfigureMockMvc
@Transactional
class EntryPaymentIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostProfileRepository: HostProfileRepository
    @Autowired private lateinit var coinService: CoinService

    private var studentId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String

    @BeforeEach
    fun setUp() {
        val hostId = userService.loginOrRegister(AuthProvider.GOOGLE, "ep-host", null, "호스트", null).user.id
        val profile = hostProfileRepository.findByUserId(hostId) ?: HostProfile(userId = hostId)
        profile.applyLevel(3)
        hostProfileRepository.saveAndFlush(profile)
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken

        studentId = userService.loginOrRegister(AuthProvider.GOOGLE, "ep-student", null, "학생", null).user.id
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken
    }

    @Test
    fun `참가비를 내면 코인이 줄고 결제 번호가 나온다`() {
        give(5000)
        val roomId = paidRoom(fee = 1000)

        val body = pay(roomId).andExpect(status().isCreated).andReturn().json()

        assertThat(body.get("amount").asInt()).isEqualTo(1000)
        assertThat(body.get("balanceAfter").asInt()).isEqualTo(4000)
        assertThat(body.get("paymentNo").asText()).matches("PM-\\d{4}-\\d{4}-\\d{4}")
    }

    @Test
    fun `잔액이 모자라면 402 로 막고 부족한 코인을 알려준다`() {
        give(300)
        val roomId = paidRoom(fee = 1000)

        val body = pay(roomId)
            .andExpect(status().isPaymentRequired)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_COINS"))
            .andReturn().json()

        assertThat(body.get("data").get("required").asInt()).isEqualTo(1000)
        assertThat(body.get("data").get("balance").asInt()).isEqualTo(300)
        assertThat(body.get("data").get("shortfall").asInt()).isEqualTo(700)
    }

    @Test
    fun `지갑이 아예 없어도 부족분은 참가비 전액이다`() {
        val roomId = paidRoom(fee = 1000)

        val body = pay(roomId).andExpect(status().isPaymentRequired).andReturn().json()

        assertThat(body.get("data").get("shortfall").asInt()).isEqualTo(1000)
    }

    @Test
    fun `같은 방에 두 번 결제할 수 없다`() {
        give(5000)
        val roomId = paidRoom(fee = 1000)
        pay(roomId).andExpect(status().isCreated)

        pay(roomId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_PAID"))
    }

    @Test
    fun `무료 방에는 참가비를 낼 수 없다`() {
        give(5000)
        val roomId = freeRoom()

        pay(roomId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("NOT_PAID_ROOM"))
    }

    @Test
    fun `결제하면 코인 원장에 방 제목과 결제 번호가 남는다`() {
        give(5000)
        val roomId = paidRoom(fee = 1000)
        val paymentNo = pay(roomId).andReturn().json().get("paymentNo").asText()

        val latest = mockMvc.perform(
            get("/users/me/coins").header("Authorization", "Bearer $studentToken"),
        ).andReturn().json().get("lastTransaction")

        assertThat(latest.get("type").asText()).isEqualTo("ENTRY")
        assertThat(latest.get("amount").asInt()).isEqualTo(-1000)
        assertThat(latest.get("description").asText()).contains("유료 방").contains(paymentNo)
    }

    @Test
    fun `결제하지 않고 유료 방에 입장할 수 없다`() {
        val roomId = paidRoom(fee = 1000)

        join(roomId)
            .andExpect(status().isPaymentRequired)
            .andExpect(jsonPath("$.code").value("ENTRY_FEE_REQUIRED"))
    }

    @Test
    fun `결제하면 유료 방에 입장할 수 있다`() {
        give(5000)
        val roomId = paidRoom(fee = 1000)
        pay(roomId).andExpect(status().isCreated)

        join(roomId).andExpect(status().isCreated)
    }

    @Test
    fun `무료 방 입장은 결제 없이 그대로 된다`() {
        join(freeRoom()).andExpect(status().isCreated)
    }

    @Test
    fun `게스트는 참가비를 낼 수 없다`() {
        val roomId = paidRoom(fee = 1000)

        mockMvc.perform(post("/rooms/{id}/entry-payments", roomId))
            .andExpect(status().isUnauthorized)
    }

    private fun give(coins: Int) {
        coinService.refund(studentId, coins, CoinRefType.ENTRY_PAYMENT, 999L, "테스트 지급")
    }

    private fun paidRoom(fee: Int): Long = createRoom("""{"title":"유료 방","type":"PAID","fee":$fee}""")

    private fun freeRoom(): Long = createRoom("""{"title":"무료 방","type":"FREE"}""")

    private fun createRoom(body: String): Long = mockMvc.perform(
        post("/rooms").header("Authorization", "Bearer $hostToken")
            .contentType(MediaType.APPLICATION_JSON).content(body),
    ).andExpect(status().isCreated).andReturn().json().get("id").asLong()

    private fun pay(roomId: Long): ResultActions = mockMvc.perform(
        post("/rooms/{id}/entry-payments", roomId).header("Authorization", "Bearer $studentToken"),
    )

    private fun join(roomId: Long): ResultActions = mockMvc.perform(
        post("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $studentToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"nickname":"학생"}"""),
    )

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
