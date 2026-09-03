package kr.passmate.coin.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 포트원 설정이 갖춰졌는지 판단하는 규칙.
 *
 * 하나라도 비면 결제 경로를 502 로 막는다 — 설정이 빠진 채 결제창을 띄우면
 * 사용자는 돈을 냈는데 우리는 그 결제를 조회할 수 없다.
 */
class PortOnePropertiesTest {

    @Test
    fun `네 값이 모두 있으면 설정된 것으로 본다`() {
        assertThat(props().isConfigured).isTrue()
    }

    @Test
    fun `상점 ID 가 비면 설정되지 않은 것이다`() {
        assertThat(props(storeId = "").isConfigured).isFalse()
    }

    @Test
    fun `채널 키가 비면 설정되지 않은 것이다`() {
        assertThat(props(channelKey = "").isConfigured).isFalse()
    }

    @Test
    fun `API Secret 이 비면 설정되지 않은 것이다`() {
        assertThat(props(apiSecret = "").isConfigured).isFalse()
    }

    @Test
    fun `웹훅 시크릿은 없어도 결제 자체는 설정된 것으로 본다`() {
        // 웹훅은 누락 방지용 보조 경로다. 없다고 결제창까지 막으면 얻는 것보다 잃는 게 크다
        assertThat(props(webhookSecret = "").isConfigured).isTrue()
        assertThat(props(webhookSecret = "").isWebhookConfigured).isFalse()
    }

    private fun props(
        storeId: String = "store-1",
        channelKey: String = "channel-key-1",
        apiSecret: String = "secret",
        webhookSecret: String = "whsec",
    ) = PortOneProperties(
        baseUrl = "https://api.portone.io",
        storeId = storeId,
        channelKey = channelKey,
        apiSecret = apiSecret,
        webhookSecret = webhookSecret,
        timeoutSeconds = 10,
    )
}
