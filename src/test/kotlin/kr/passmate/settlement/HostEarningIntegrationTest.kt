package kr.passmate.settlement

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.service.RoomService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 호스트 수익·정산 내역 조회와 CSV 내보내기 (FR-055 · FR-056).
 *
 * 적립은 유료 세션이 끝날 때 일어나는데 유료 방이 아직 막혀 있어,
 * 여기서는 적립 행을 직접 깔고 **집계와 표시**를 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class HostEarningIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostEarningRepository: kr.passmate.settlement.repository.HostEarningRepository

    private var hostId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        hostId = member("earn-host")
        token = jwtTokenProvider.issue(hostId, false).accessToken
    }

    @Test
    fun `수익이 없으면 0 으로 답한다`() {
        val body = earnings().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("thisMonthNet").asInt()).isZero()
        assertThat(body.get("pendingNet").asInt()).isZero()
        assertThat(body.get("earnings")).isEmpty()
    }

    @Test
    fun `세션별 적립 내역이 참가비와 수수료로 나뉘어 보인다`() {
        earning(gross = 10_000, participants = 10)

        val row = earnings().andExpect(status().isOk).andReturn().json().get("earnings")[0]

        assertThat(row.get("gross").asInt()).isEqualTo(10_000)
        // 80:20 배분(FR-055)
        assertThat(row.get("platformFee").asInt()).isEqualTo(2_000)
        assertThat(row.get("net").asInt()).isEqualTo(8_000)
        assertThat(row.get("participantCount").asInt()).isEqualTo(10)
        assertThat(row.get("status").asText()).isEqualTo("PENDING")
        assertThat(row.get("roomTitle").asText()).isEqualTo("유료 방")
    }

    @Test
    fun `이번 달 수익과 지급 예정액을 함께 준다`() {
        earning(gross = 10_000, participants = 10)
        earning(gross = 5_000, participants = 5)

        val body = earnings().andReturn().json()

        assertThat(body.get("thisMonthNet").asInt()).isEqualTo(12_000)
        assertThat(body.get("pendingNet").asInt()).isEqualTo(12_000)
        assertThat(body.get("nextPayoutDate").isNull).isFalse()
    }

    @Test
    fun `지난달 적립은 이번 달 수익에서 빠진다`() {
        earning(gross = 10_000, participants = 10, earnedAt = LocalDateTime.now().minusMonths(2))

        val body = earnings().andReturn().json()

        assertThat(body.get("thisMonthNet").asInt()).isZero()
        // 아직 지급되지 않았으니 지급 예정액에는 남는다
        assertThat(body.get("pendingNet").asInt()).isEqualTo(8_000)
        assertThat(body.get("earnings")).hasSize(1)
    }

    @Test
    fun `남의 적립은 내 내역에 들어오지 않는다`() {
        val otherId = member("earn-other")
        earning(gross = 10_000, participants = 10, hostUserId = otherId)

        assertThat(earnings().andReturn().json().get("earnings")).isEmpty()
    }

    @Test
    fun `계좌를 등록하지 않았으면 정산 보류로 알린다`() {
        earning(gross = 10_000, participants = 10)

        earnings()
            .andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.accountRegistered").value(false))
    }

    @Test
    fun `CSV 로 내보낸다`() {
        earning(gross = 10_000, participants = 10)

        val response = export().andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
            .andReturn().response

        val csv = response.contentAsString
        // 엑셀이 UTF-8 로 열도록 BOM 을 앞에 둔다
        assertThat(csv).startsWith("﻿")
        assertThat(csv).contains("유료 방")
        assertThat(csv).contains("10000")
        assertThat(csv).contains("8000")
    }

    @Test
    fun `csv 가 아닌 형식은 거절한다`() {
        mockMvc.perform(get("/users/me/earnings/export").param("format", "pdf").header(AUTH, "Bearer $token"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `로그인하지 않으면 수익을 볼 수 없다`() {
        mockMvc.perform(get("/users/me/earnings")).andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    private fun earning(
        gross: Int,
        participants: Int,
        hostUserId: Long = hostId,
        earnedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        val room = roomService.create(hostUserId, RoomCreateRequest(title = "유료 방", type = RoomType.FREE))
        hostEarningRepository.saveAndFlush(
            kr.passmate.settlement.domain.HostEarning.of(
                roomId = room.id,
                hostUserId = hostUserId,
                participantCount = participants,
                gross = gross,
                hostRate = 0.8,
                earnedAt = earnedAt,
            ),
        )
    }

    private fun earnings(): ResultActions =
        mockMvc.perform(get("/users/me/earnings").header(AUTH, "Bearer $token"))

    private fun export(): ResultActions =
        mockMvc.perform(get("/users/me/earnings/export").header(AUTH, "Bearer $token"))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
