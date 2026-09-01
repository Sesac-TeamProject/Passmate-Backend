package kr.passmate.common.security

import kr.passmate.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * CORS 허용 출처가 **설정값에서** 온다는 것을 고정한다.
 *
 * 예전에는 도메인이 `SecurityConfig` 에 박혀 있었다. 그 상태로 도메인이 바뀌면
 * 코드를 고쳐 다시 빌드해야 하고, 웹 팀은 원인을 알기 어려운 CORS 오류만 본다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["passmate.cors.allowed-origins=https://passmate.kr"])
class CorsIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `설정한 출처의 프리플라이트는 통과시킨다`() {
        mockMvc.perform(
            options("/rooms/public")
                .header("Origin", "https://passmate.kr")
                .header("Access-Control-Request-Method", "GET"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "https://passmate.kr"))
    }

    @Test
    fun `설정에 없는 출처는 막는다`() {
        mockMvc.perform(
            options("/rooms/public")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET"),
        )
            .andExpect(status().isForbidden)
    }
}
