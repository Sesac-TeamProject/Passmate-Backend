package kr.passmate.notification

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.notification.repository.DeviceTokenRepository
import kr.passmate.notification.service.NotificationKind
import kr.passmate.notification.service.NotificationSettingService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 설정과 푸시 토큰 등록 (FR-065).
 *
 * 발송 자체는 Firebase 설정이 있어야 해서 여기 범위가 아니다 —
 * "설정을 저장하고 읽는다"와 "토큰이 쌓이지 않는다"만 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired private lateinit var notificationSettingService: NotificationSettingService

    private var userId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        userId = member("noti-user")
        token = jwtTokenProvider.issue(userId, false).accessToken
    }

    @Test
    fun `설정한 적 없으면 전부 켜짐으로 시작한다`() {
        settings()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionStart").value(true))
            .andExpect(jsonPath("$.ratingRequest").value(true))
            .andExpect(jsonPath("$.settlementDone").value(true))
    }

    @Test
    fun `항목별로 끄고 켤 수 있다`() {
        update(sessionStart = false, ratingRequest = true, settlementDone = false)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionStart").value(false))
            .andExpect(jsonPath("$.ratingRequest").value(true))
            .andExpect(jsonPath("$.settlementDone").value(false))

        settings()
            .andExpect(jsonPath("$.sessionStart").value(false))
            .andExpect(jsonPath("$.settlementDone").value(false))
    }

    @Test
    fun `여러 번 저장해도 행이 늘지 않는다`() {
        update(false, false, false).andExpect(status().isOk)
        update(true, true, true).andExpect(status().isOk)

        settings().andExpect(jsonPath("$.sessionStart").value(true))
    }

    @Test
    fun `발송하는 쪽은 설정을 보고 보낼지 정한다`() {
        assertThat(notificationSettingService.allows(userId, NotificationKind.RATING_REQUEST)).isTrue()

        update(sessionStart = true, ratingRequest = false, settlementDone = true).andExpect(status().isOk)

        assertThat(notificationSettingService.allows(userId, NotificationKind.RATING_REQUEST)).isFalse()
        assertThat(notificationSettingService.allows(userId, NotificationKind.SESSION_START)).isTrue()
    }

    @Test
    fun `설정 행이 아직 없으면 받는 것으로 본다`() {
        assertThat(notificationSettingService.allows(member("noti-fresh"), NotificationKind.SESSION_START))
            .isTrue()
    }

    @Test
    fun `세 항목을 다 보내지 않으면 거절한다`() {
        mockMvc.perform(
            put("/users/me/notification-settings")
                .header(AUTH, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sessionStart": false}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `푸시 토큰을 등록한다`() {
        val body = registerDevice("ANDROID", "fcm-token-1")
            .andExpect(status().isCreated).andReturn().json()

        assertThat(body.get("platform").asText()).isEqualTo("ANDROID")
        assertThat(body.get("lastSeenAt").isNull).isFalse()
        // 토큰 값은 돌려주지 않는다 — 보낸 쪽이 이미 갖고 있고, 남기면 로그에 샌다
        assertThat(body.has("token")).isFalse()
    }

    @Test
    fun `같은 토큰을 다시 보내도 행이 쌓이지 않는다`() {
        registerDevice("ANDROID", "fcm-token-1").andExpect(status().isCreated)
        registerDevice("ANDROID", "fcm-token-1").andExpect(status().isCreated)

        assertThat(deviceTokenRepository.findAllByUserId(userId)).hasSize(1)
    }

    @Test
    fun `기기를 여러 대 쓰면 토큰도 여러 개 남는다`() {
        registerDevice("ANDROID", "fcm-token-1").andExpect(status().isCreated)
        registerDevice("IOS", "apns-token-2").andExpect(status().isCreated)

        assertThat(deviceTokenRepository.findAllByUserId(userId)).hasSize(2)
    }

    @Test
    fun `같은 기기를 다른 계정이 쓰면 토큰 주인이 바뀐다`() {
        registerDevice("ANDROID", "shared-device").andExpect(status().isCreated)

        val otherId = member("noti-other")
        val otherToken = jwtTokenProvider.issue(otherId, false).accessToken
        mockMvc.perform(
            post("/users/me/devices")
                .header(AUTH, "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("platform" to "ANDROID", "token" to "shared-device"))),
        ).andExpect(status().isCreated)

        // 옛 주인 앞으로는 더 이상 안 간다
        assertThat(deviceTokenRepository.findAllByUserId(userId)).isEmpty()
        assertThat(deviceTokenRepository.findAllByUserId(otherId)).hasSize(1)
    }

    @Test
    fun `빈 토큰은 거절한다`() {
        registerDevice("ANDROID", "  ").andExpect(status().isBadRequest)
    }

    @Test
    fun `모르는 플랫폼은 거절한다`() {
        registerDevice("WINDOWS_PHONE", "t").andExpect(status().isBadRequest)
    }

    @Test
    fun `로그인하지 않으면 설정을 볼 수 없다`() {
        mockMvc.perform(get("/users/me/notification-settings")).andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    private fun settings(): ResultActions =
        mockMvc.perform(get("/users/me/notification-settings").header(AUTH, "Bearer $token"))

    private fun update(sessionStart: Boolean, ratingRequest: Boolean, settlementDone: Boolean): ResultActions =
        mockMvc.perform(
            put("/users/me/notification-settings")
                .header(AUTH, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionStart" to sessionStart,
                            "ratingRequest" to ratingRequest,
                            "settlementDone" to settlementDone,
                        ),
                    ),
                ),
        )

    private fun registerDevice(platform: String, deviceToken: String): ResultActions =
        mockMvc.perform(
            post("/users/me/devices")
                .header(AUTH, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("platform" to platform, "token" to deviceToken))),
        )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
