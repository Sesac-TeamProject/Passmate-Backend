package kr.passmate.ad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.support.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 광고 조회의 기본 계약 (FR-073).
 *
 * 광고는 결과 화면·대기실 배너에 뜨므로 **게스트도 봐야 한다** — 로그인 없이 열린다.
 */
@AutoConfigureMockMvc
@Transactional
class AdQueryIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `집행 중인 광고가 없으면 빈 목록이다`() {
        val body = mockMvc.perform(get("/ads").param("placement", "RESULT_BOTTOM"))
            .andExpect(status().isOk).andReturn().json()

        assertThat(body.get("ads")).isEmpty()
    }

    @Test
    fun `로그인하지 않아도 조회된다`() {
        mockMvc.perform(get("/ads").param("placement", "WAITING_ROOM_BANNER"))
            .andExpect(status().isOk)
    }

    @Test
    fun `노출 위치는 필수다`() {
        mockMvc.perform(get("/ads")).andExpect(status().isBadRequest)
    }

    @Test
    fun `모르는 노출 위치는 거절한다`() {
        mockMvc.perform(get("/ads").param("placement", "SIDEBAR"))
            .andExpect(status().isBadRequest)
    }

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
