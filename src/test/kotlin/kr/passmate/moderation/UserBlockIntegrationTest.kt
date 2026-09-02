package kr.passmate.moderation

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 호스트 차단·해제와 차단 목록 (FR-067).
 */
@AutoConfigureMockMvc
@Transactional
class UserBlockIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var meId: Long = 0
    private var hostId: Long = 0
    private lateinit var myToken: String

    @BeforeEach
    fun setUp() {
        meId = member("block-me")
        hostId = member("block-host")
        myToken = jwtTokenProvider.issue(meId, false).accessToken
    }

    @Test
    fun `차단하면 차단 목록에 남는다`() {
        block(hostId).andExpect(status().isNoContent)

        val body = blocks().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("blocks")).hasSize(1)
        assertThat(body.get("blocks")[0].get("userId").asLong()).isEqualTo(hostId)
        assertThat(body.get("blocks")[0].get("nickname").asText()).isEqualTo("block-host")
        assertThat(body.get("blocks")[0].get("blockedAt").isNull).isFalse()
    }

    @Test
    fun `차단 목록에는 등급도 함께 나온다`() {
        block(hostId).andExpect(status().isNoContent)

        blocks()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blocks[0].level").value(1))
    }

    @Test
    fun `같은 사람을 두 번 차단해도 목록이 늘지 않는다`() {
        block(hostId).andExpect(status().isNoContent)
        block(hostId).andExpect(status().isNoContent)

        assertThat(blocks().andReturn().json().get("blocks")).hasSize(1)
    }

    @Test
    fun `차단을 해제하면 목록에서 빠진다`() {
        block(hostId).andExpect(status().isNoContent)

        unblock(hostId).andExpect(status().isNoContent)

        assertThat(blocks().andReturn().json().get("blocks")).isEmpty()
    }

    @Test
    fun `차단한 적 없는 사람을 해제해도 조용히 끝난다`() {
        unblock(hostId).andExpect(status().isNoContent)
    }

    @Test
    fun `자기 자신은 차단할 수 없다`() {
        block(meId)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `없는 사용자는 차단할 수 없다`() {
        block(999_999)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `로그인하지 않으면 차단할 수 없다`() {
        mockMvc.perform(post("/users/{id}/block", hostId)).andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    private fun block(userId: Long): ResultActions =
        mockMvc.perform(post("/users/{id}/block", userId).header(AUTH, "Bearer $myToken"))

    private fun unblock(userId: Long): ResultActions =
        mockMvc.perform(delete("/users/{id}/block", userId).header(AUTH, "Bearer $myToken"))

    private fun blocks(): ResultActions =
        mockMvc.perform(get("/users/me/blocks").header(AUTH, "Bearer $myToken"))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
