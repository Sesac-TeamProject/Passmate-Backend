package kr.passmate.room

import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.repository.HostProfileRepository
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 유료 방 개설 (FR-046 · FR-050).
 *
 * 참가비를 받으려면 **Lv.3 이상**이어야 한다 — 등급은 방을 운영해 본 이력이라,
 * 아무나 돈을 받게 열면 참가비만 걷고 세션을 안 여는 방을 막을 수 없다.
 */
@AutoConfigureMockMvc
@Transactional
class PaidRoomCreateIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostProfileRepository: HostProfileRepository

    private var hostId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        hostId = userService.loginOrRegister(AuthProvider.GOOGLE, "paid-host", null, "유료호스트", null).user.id
        token = jwtTokenProvider.issue(hostId, false).accessToken
    }

    @Test
    fun `Lv3 미만 호스트는 유료 방을 열 수 없다`() {
        levelOf(hostId, 2)

        create(type = "PAID", fee = 1000)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("HOST_LEVEL_REQUIRED"))
    }

    @Test
    fun `등급 이력이 아예 없는 호스트도 유료 방을 열 수 없다`() {
        create(type = "PAID", fee = 1000)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("HOST_LEVEL_REQUIRED"))
    }

    @Test
    fun `Lv3 이상이면 참가비를 받는 방을 연다`() {
        levelOf(hostId, 3)

        create(type = "PAID", fee = 1000)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("PAID"))
            .andExpect(jsonPath("$.fee").value(1000))
    }

    @Test
    fun `유료 방인데 참가비가 없으면 거절한다`() {
        levelOf(hostId, 3)

        create(type = "PAID", fee = null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `참가비가 정책 하한보다 낮으면 거절한다`() {
        levelOf(hostId, 3)

        create(type = "PAID", fee = 50)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `참가비가 정책 상한보다 높으면 거절한다`() {
        levelOf(hostId, 3)

        create(type = "PAID", fee = 10001)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `무료 방은 등급과 상관없이 연다`() {
        create(type = "FREE", fee = null).andExpect(status().isCreated)
    }

    @Test
    fun `무료 방에 참가비를 붙이면 거절한다`() {
        create(type = "FREE", fee = 1000)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `브랜디드 방은 아직 열리지 않는다`() {
        levelOf(hostId, 5)

        create(type = "BRANDED", fee = 1000)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_ROOM_TYPE"))
    }

    private fun levelOf(userId: Long, level: Int) {
        val profile = hostProfileRepository.findByUserId(userId) ?: HostProfile(userId = userId)
        profile.applyLevel(level)
        hostProfileRepository.saveAndFlush(profile)
    }

    private fun create(type: String, fee: Int?): ResultActions {
        val body = buildMap<String, Any> {
            put("title", "유료 방")
            put("type", type)
            fee?.let { put("fee", it) }
        }
        return mockMvc.perform(
            post("/rooms").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )
    }
}
