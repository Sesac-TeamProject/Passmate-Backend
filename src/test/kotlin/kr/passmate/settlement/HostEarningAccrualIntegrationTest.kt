package kr.passmate.settlement

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.service.CoinService
import kr.passmate.common.event.SessionEndedEvent
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.repository.HostProfileRepository
import kr.passmate.room.repository.RoomRepository
import kr.passmate.settlement.repository.HostEarningRepository
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 세션이 끝나면 호스트 수익이 적립된다 (FR-055).
 *
 * 이 경로가 없으면 참가비는 걷히는데 정산 조회는 영원히 빈 목록이다 —
 * host_earning 을 만드는 생산 코드는 여기 하나뿐이다.
 */
@AutoConfigureMockMvc
@Transactional
class HostEarningAccrualIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostProfileRepository: HostProfileRepository
    @Autowired private lateinit var roomRepository: RoomRepository
    @Autowired private lateinit var hostEarningRepository: HostEarningRepository
    @Autowired private lateinit var coinService: CoinService
    @Autowired private lateinit var eventPublisher: ApplicationEventPublisher

    private var hostId: Long = 0
    private lateinit var hostToken: String
    private val studentTokens = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        hostId = userService.loginOrRegister(AuthProvider.GOOGLE, "he-host", null, "호스트", null).user.id
        val profile = hostProfileRepository.findByUserId(hostId) ?: HostProfile(userId = hostId)
        profile.applyLevel(3)
        hostProfileRepository.saveAndFlush(profile)
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken

        repeat(3) { i ->
            val id = userService.loginOrRegister(AuthProvider.GOOGLE, "he-s$i", null, "학생$i", null).user.id
            coinService.refund(id, 10_000, CoinRefType.ENTRY_PAYMENT, 900L + i, "테스트 지급")
            studentTokens += jwtTokenProvider.issue(id, false).accessToken
        }
    }

    @Test
    fun `유료 세션이 끝나면 걷힌 참가비가 수익으로 적립된다`() {
        val roomId = paidRoom(1000)
        studentTokens.forEach { pay(roomId, it) }

        endSession(roomId)

        val earning = hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostId).single()
        assertThat(earning.roomId).isEqualTo(roomId)
        assertThat(earning.participantCount).isEqualTo(3)
        assertThat(earning.gross).isEqualTo(3000)
    }

    @Test
    fun `배분은 호스트 80 플랫폼 20 이다`() {
        val roomId = paidRoom(1000)
        studentTokens.forEach { pay(roomId, it) }

        endSession(roomId)

        val earning = hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostId).single()
        assertThat(earning.platformFee).isEqualTo(600)
        assertThat(earning.net).isEqualTo(2400)
        assertThat(earning.platformFee + earning.net).isEqualTo(earning.gross)
    }

    @Test
    fun `환급된 참가비는 수익에 들어가지 않는다`() {
        val roomId = paidRoom(1000)
        val paymentId = pay(roomId, studentTokens[0])
        pay(roomId, studentTokens[1])
        cancel(paymentId, studentTokens[0])

        endSession(roomId)

        val earning = hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostId).single()
        assertThat(earning.gross).isEqualTo(1000)
        assertThat(earning.participantCount).isEqualTo(1)
    }

    @Test
    fun `무료 세션은 수익이 적립되지 않는다`() {
        val roomId = freeRoom()

        endSession(roomId)

        assertThat(hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostId)).isEmpty()
    }

    @Test
    fun `아무도 결제하지 않았으면 적립하지 않는다`() {
        val roomId = paidRoom(1000)

        endSession(roomId)

        assertThat(hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostId)).isEmpty()
    }

    @Test
    fun `종료 이벤트가 두 번 와도 수익은 한 줄이다`() {
        val roomId = paidRoom(1000)
        pay(roomId, studentTokens[0])

        endSession(roomId)
        eventPublisher.publishEvent(SessionEndedEvent(roomId))

        assertThat(hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostId)).hasSize(1)
    }

    @Test
    fun `적립된 수익은 정산 내역 조회에 나온다`() {
        val roomId = paidRoom(1000)
        studentTokens.forEach { pay(roomId, it) }
        endSession(roomId)

        val body = mockMvc.perform(
            get("/users/me/earnings").header("Authorization", "Bearer $hostToken"),
        ).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("pendingNet").asInt()).isEqualTo(2400)
        val row = body.get("earnings")[0]
        assertThat(row.get("roomTitle").asText()).isEqualTo("유료 방")
        assertThat(row.get("gross").asInt()).isEqualTo(3000)
        assertThat(row.get("status").asText()).isEqualTo("PENDING")
    }

    /** 문제 세트 없이 종료 경로만 확인한다 — 적립은 방 상태와 결제만 보면 된다. */
    private fun endSession(roomId: Long) {
        val room = roomRepository.findById(roomId).get()
        room.start()
        room.close()
        roomRepository.flush()
        eventPublisher.publishEvent(SessionEndedEvent(roomId))
    }

    private fun paidRoom(fee: Int): Long = createRoom("""{"title":"유료 방","type":"PAID","fee":$fee}""")

    private fun freeRoom(): Long = createRoom("""{"title":"무료 방","type":"FREE"}""")

    private fun createRoom(body: String): Long = mockMvc.perform(
        post("/rooms").header("Authorization", "Bearer $hostToken")
            .contentType(MediaType.APPLICATION_JSON).content(body),
    ).andExpect(status().isCreated).andReturn().json().get("id").asLong()

    private fun pay(roomId: Long, token: String): Long = mockMvc.perform(
        post("/rooms/{id}/entry-payments", roomId).header("Authorization", "Bearer $token"),
    ).andExpect(status().isCreated).andReturn().json().get("paymentId").asLong()

    private fun cancel(paymentId: Long, token: String) {
        mockMvc.perform(post("/entry-payments/{id}/cancel", paymentId).header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
