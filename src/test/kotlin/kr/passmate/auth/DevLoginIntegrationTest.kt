package kr.passmate.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.support.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * dev 프로파일에서만 열리는 개발용 로그인.
 * 같은 key 는 같은 계정으로 이어져야 웹·앱 팀이 고정 계정으로 붙어볼 수 있다.
 * (test 프로파일 단독에서 404 인 것은 AuthIntegrationTest 가 검증한다)
 */
@ActiveProfiles("test", "dev")
@AutoConfigureMockMvc
@Transactional
class DevLoginIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `dev 프로파일에서는 key 만으로 로그인되고 같은 key 는 같은 계정이다`() {
        val first = devLogin("tester")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(true))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn().json()
        val userId = first.get("user").get("id").asLong()

        val second = devLogin("tester")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(false))
            .andReturn().json()
        assertThat(second.get("user").get("id").asLong()).isEqualTo(userId)

        // 발급된 토큰이 실제 API 에 통하는지까지 확인한다
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer ${first.get("accessToken").asText()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
    }

    @Test
    fun `key 가 없으면 400 이다`() {
        mockMvc.perform(
            post("/auth/dev-login").contentType(MediaType.APPLICATION_JSON).content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    private fun devLogin(key: String) = mockMvc.perform(
        post("/auth/dev-login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"key":"$key"}"""),
    )

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
