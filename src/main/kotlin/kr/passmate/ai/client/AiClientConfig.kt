package kr.passmate.ai.client

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * OpenAI 호출용 RestClient.
 *
 * base URL·타임아웃을 **클라이언트 바깥**에서 잡는다 — 클라이언트가 스스로 요청 팩토리를
 * 갈아끼우면 테스트가 붙인 목 서버까지 밀어내 실제 OpenAI 로 요청이 나간다.
 * 유료 API 라 그 사고의 대가가 크다(.claude/CLAUDE.md ⛔ 규칙).
 */
@Configuration
class AiClientConfig {

    @Bean(OPENAI_REST_CLIENT)
    fun openAiRestClient(builder: RestClient.Builder, properties: AiProperties): RestClient =
        builder
            .baseUrl(properties.baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT)
                    setReadTimeout(properties.timeout)
                },
            )
            .build()

    companion object {
        /** RestClient 빈이 여럿이라 이름으로 구분한다 */
        const val OPENAI_REST_CLIENT = "openAiRestClient"

        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
