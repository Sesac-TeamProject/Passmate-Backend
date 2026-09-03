package kr.passmate.room

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.service.CoinService
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.repository.HostProfileRepository
import kr.passmate.room.repository.RoomRepository
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
 * 참가비 취소 = 코인 100% 환급 (FR-052). **현금 환불이 아니다.**
 *
 * 되돌릴 수 있는 것은 **세션 시작 전까지**다 — 시작한 뒤 학생 사유로 나가는 것은
 * 이미 제공된 세션이라 환급하지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
class EntryPaymentCancelIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostProfileRepository: HostProfileRepository
    @Autowired private lateinit var roomRepository: RoomRepository
    @Autowired private lateinit var coinService: CoinService

    private var studentId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setUp() {
        val hostId = userService.loginOrRegister(AuthProvider.GOOGLE, "cx-host", null, "호스트", null).user.id
        val profile = hostProfileRepository.findByUserId(hostId) ?: HostProfile(userId = hostId)
        profile.applyLevel(3)
        hostProfileRepository.saveAndFlush(profile)
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken

        studentId = userService.loginOrRegister(AuthProvider.GOOGLE, "cx-student", null, "학생", null).user.id
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val otherId = userService.loginOrRegister(AuthProvider.GOOGLE, "cx-other", null, "남", null).user.id
        otherToken = jwtTokenProvider.issue(otherId, false).accessToken
    }

    @Test
    fun `세션 시작 전에 취소하면 코인이 전부 돌아온다`() {
        give(5000)
        val paymentId = payFor(paidRoom(1000))

        val body = cancel(paymentId, studentToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("status").asText()).isEqualTo("REFUNDED")
        assertThat(body.get("refundedAmount").asInt()).isEqualTo(1000)
        assertThat(body.get("balanceAfter").asInt()).isEqualTo(5000)
    }

    @Test
    fun `취소하면 원장에 환급 한 줄이 쌓인다`() {
        give(5000)
        val paymentId = payFor(paidRoom(1000))
        cancel(paymentId, studentToken).andExpect(status().isOk)

        val latest = mockMvc.perform(
            get("/users/me/coins").header("Authorization", "Bearer $studentToken"),
        ).andReturn().json().get("lastTransaction")

        assertThat(latest.get("type").asText()).isEqualTo("REFUND")
        assertThat(latest.get("amount").asInt()).isEqualTo(1000)
    }

    @Test
    fun `이미 취소한 결제는 다시 취소할 수 없다`() {
        give(5000)
        val paymentId = payFor(paidRoom(1000))
        cancel(paymentId, studentToken).andExpect(status().isOk)

        cancel(paymentId, studentToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_REFUNDED"))
    }

    @Test
    fun `두 번 취소해도 코인이 두 배로 돌아오지는 않는다`() {
        give(5000)
        val paymentId = payFor(paidRoom(1000))
        cancel(paymentId, studentToken)
        cancel(paymentId, studentToken)

        val balance = mockMvc.perform(
            get("/users/me/coins").header("Authorization", "Bearer $studentToken"),
        ).andReturn().json().get("balance").asInt()

        assertThat(balance).isEqualTo(5000)
    }

    @Test
    fun `남의 결제는 취소할 수 없다`() {
        give(5000)
        val paymentId = payFor(paidRoom(1000))

        cancel(paymentId, otherToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `세션이 시작되면 환급되지 않는다`() {
        give(5000)
        val roomId = paidRoom(1000)
        val paymentId = payFor(roomId)
        startSession(roomId)

        cancel(paymentId, studentToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("REFUND_WINDOW_CLOSED"))
    }

    @Test
    fun `취소하면 입장했던 참가자도 방에서 빠진다`() {
        give(5000)
        val roomId = paidRoom(1000)
        val paymentId = payFor(roomId)
        join(roomId).andExpect(status().isCreated)

        cancel(paymentId, studentToken).andExpect(status().isOk)

        val participants = mockMvc.perform(
            get("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $hostToken"),
        ).andReturn().json()
        assertThat(participants.size()).isZero()
    }

    @Test
    fun `취소한 뒤에는 같은 방에 다시 결제할 수 있다`() {
        give(5000)
        val roomId = paidRoom(1000)
        cancel(payFor(roomId), studentToken).andExpect(status().isOk)

        mockMvc.perform(post("/rooms/{id}/entry-payments", roomId).header("Authorization", "Bearer $studentToken"))
            .andExpect(status().isCreated)
    }

    @Test
    fun `없는 결제를 취소하면 404`() {
        cancel(999_999L, studentToken).andExpect(status().isNotFound)
    }

    private fun give(coins: Int) {
        coinService.refund(studentId, coins, CoinRefType.ENTRY_PAYMENT, 999L, "테스트 지급")
    }

    private fun startSession(roomId: Long) {
        roomRepository.findById(roomId).get().start()
        roomRepository.flush()
    }

    private fun paidRoom(fee: Int): Long = mockMvc.perform(
        post("/rooms").header("Authorization", "Bearer $hostToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":"유료 방","type":"PAID","fee":$fee}"""),
    ).andExpect(status().isCreated).andReturn().json().get("id").asLong()

    private fun payFor(roomId: Long): Long = mockMvc.perform(
        post("/rooms/{id}/entry-payments", roomId).header("Authorization", "Bearer $studentToken"),
    ).andExpect(status().isCreated).andReturn().json().get("paymentId").asLong()

    private fun join(roomId: Long): ResultActions = mockMvc.perform(
        post("/rooms/{id}/participants", roomId).header("Authorization", "Bearer $studentToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"nickname":"학생"}"""),
    )

    private fun cancel(paymentId: Long, token: String): ResultActions = mockMvc.perform(
        post("/entry-payments/{id}/cancel", paymentId).header("Authorization", "Bearer $token"),
    )

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
