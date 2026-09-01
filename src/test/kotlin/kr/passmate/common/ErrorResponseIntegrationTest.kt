package kr.passmate.common

import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 잘못 보낸 요청은 **400** 이어야 한다.
 *
 * 이걸 안 잡으면 마지막 Exception 핸들러로 떨어져 500 이 나가고,
 * 클라이언트 잘못이 서버 장애처럼 보인다 — 재시도해도 될 것처럼 읽히는 게 더 나쁘다.
 */
@AutoConfigureMockMvc
@Transactional
class ErrorResponseIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val outcome = userService.loginOrRegister(AuthProvider.GOOGLE, "err-user", "err@example.com", "err", null)
        token = jwtTokenProvider.issue(outcome.user.id, outcome.user.isAdmin).accessToken
    }

    @Test
    fun `필수 필드가 빠진 본문은 400 이다`() {
        // title 없음 — Kotlin non-null 이라 Jackson 이 역직렬화 단계에서 터진다
        postSet("""{"description":"제목이 없다"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `깨진 JSON 은 400 이다`() {
        postSet("""{"title": """)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `타입이 안 맞는 값은 400 이다`() {
        postSet("""{"title":123,"description":{"nested":true}}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `본문이 아예 없으면 400 이다`() {
        mockMvc.perform(
            post("/question-sets").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `경로 변수 타입이 안 맞으면 400 이다`() {
        mockMvc.perform(get("/question-sets/{id}", "숫자아님").header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `오류 응답에 내부 클래스 이름이 새지 않는다`() {
        val body = postSet("""{"description":"제목이 없다"}""")
            .andReturn().response.contentAsString

        // 파서 메시지를 그대로 실으면 kr.passmate.* 같은 내부 구조가 응답에 실린다
        assert(!body.contains("kr.passmate")) { "응답에 내부 클래스 이름이 노출됐다: $body" }
        assert(!body.contains("Exception")) { "응답에 예외 이름이 노출됐다: $body" }
    }

    private fun postSet(body: String) = mockMvc.perform(
        post("/question-sets").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(body),
    )
}
