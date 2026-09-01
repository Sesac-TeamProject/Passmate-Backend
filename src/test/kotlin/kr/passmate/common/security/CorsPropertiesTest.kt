package kr.passmate.common.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CorsPropertiesTest {

    @Test
    fun `공백을 다듬어 허용 목록을 만든다`() {
        val properties = CorsProperties(
            allowedOrigins = listOf(" https://passmate.kr ", "http://localhost:*"),
        )

        assertThat(properties.originPatterns)
            .containsExactly("https://passmate.kr", "http://localhost:*")
    }

    /**
     * env 가 비어 있으면 `""` 하나짜리 리스트가 들어온다.
     * 그대로 두면 어떤 출처에도 맞지 않아 "설정은 했는데 전부 막히는" 상태가 조용히 만들어진다.
     */
    @Test
    fun `빈 값은 허용 목록에 넣지 않는다`() {
        val properties = CorsProperties(allowedOrigins = listOf("", "   ", "https://passmate.kr"))

        assertThat(properties.originPatterns).containsExactly("https://passmate.kr")
    }

    @Test
    fun `허용 메서드 기본값은 우리가 쓰는 메서드와 프리플라이트용 OPTIONS 다`() {
        assertThat(CorsProperties().allowedMethods)
            .containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS")
    }
}
