package kr.passmate.coin.client

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 포트원 **V2** 설정.
 *
 * 값을 코드에 박지 않는다 — 테스트 채널과 실연동 채널이 값만 다르고 코드는 같아야 한다.
 * (2026-09-03 결정: 운영도 테스트 채널을 쓴다. 실결제가 없어 PG 실계약이 필요 없다.)
 *
 * 성격이 정반대인 값이 섞여 있다.
 * - [storeId] · [channelKey] — 결제창을 띄우는 값. 클라이언트에 내려보내도 된다
 * - [apiSecret] · [webhookSecret] — **서버 전용.** 결제 조회·취소까지 되는 열쇠라 절대 밖으로 내보내지 않는다
 */
@ConfigurationProperties(prefix = "passmate.portone")
data class PortOneProperties(
    val baseUrl: String,
    /** 상점 ID(`store-...`). 결제창 호출과 결제 취소 API 가 함께 쓴다 */
    val storeId: String,
    /** 채널 키(`channel-key-...`). 어느 PG 로 결제할지 고르는 값 */
    val channelKey: String,
    /** V2 API Secret. `Authorization: PortOne {secret}` 헤더로 나간다 */
    val apiSecret: String,
    /** 웹훅 서명 검증용. 없으면 웹훅만 막고 결제 자체는 막지 않는다 */
    val webhookSecret: String,
    val timeoutSeconds: Long,
) {
    val timeout: Duration get() = Duration.ofSeconds(timeoutSeconds)

    /**
     * 결제를 시작해도 되는 상태인지. 셋 중 하나라도 비면 결제 경로를 막는다 —
     * 설정이 빠진 채 결제창을 띄우면 사용자는 돈을 냈는데 우리는 그 결제를 조회할 수 없다.
     */
    val isConfigured: Boolean
        get() = storeId.isNotBlank() && channelKey.isNotBlank() && apiSecret.isNotBlank()

    /**
     * 웹훅을 받아도 되는 상태인지. 웹훅은 **누락 방지용 보조 경로**라
     * 시크릿이 없다고 결제창까지 막으면 얻는 것보다 잃는 게 크다.
     */
    val isWebhookConfigured: Boolean get() = webhookSecret.isNotBlank()
}
