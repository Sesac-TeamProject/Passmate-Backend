package kr.passmate.support

import kr.passmate.ai.client.OpenAiClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * OpenAI 를 Fake 로 갈아끼운다. 통합 테스트에서 `@Import(FakeAiConfig::class)` 로 쓴다.
 * 이걸 붙이지 않으면 실제 HTTP 클라이언트가 올라간다 — 유료 API 를 부를 여지를 남기지 않는다.
 */
@TestConfiguration
class FakeAiConfig {

    @Bean
    @Primary
    fun fakeOpenAiClient(): OpenAiClient = FakeOpenAiClient()
}
