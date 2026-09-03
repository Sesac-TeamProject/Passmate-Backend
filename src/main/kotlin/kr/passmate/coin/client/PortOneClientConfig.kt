package kr.passmate.coin.client

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 포트원 호출용 RestClient.
 *
 * base URL·타임아웃을 **클라이언트 바깥**에서 잡는다 — 클라이언트가 스스로 요청 팩토리를
 * 갈아끼우면 테스트가 붙인 목 서버까지 밀어내 실제 결제 API 로 요청이 나간다.
 */
@Configuration
class PortOneClientConfig {

    @Bean(PORTONE_REST_CLIENT)
    fun portOneRestClient(builder: RestClient.Builder, properties: PortOneProperties): RestClient =
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
        const val PORTONE_REST_CLIENT = "portOneRestClient"

        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
