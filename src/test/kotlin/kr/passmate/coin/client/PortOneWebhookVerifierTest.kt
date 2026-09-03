package kr.passmate.coin.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 포트원 웹훅 서명 검증 (Standard Webhooks 규격).
 *
 * 이 검증이 없으면 **누구나 우리 웹훅 주소로 "결제 완료" 를 보내 코인을 받을 수 있다.**
 * 주소는 공개돼 있으므로 서명이 유일한 방벽이다.
 */
class PortOneWebhookVerifierTest {

    private val secret = "whsec_dGVzdC1zZWNyZXQtZm9yLXdlYmhvb2s="
    private val verifier = PortOneWebhookVerifier(props(secret))

    private val body = """{"type":"Transaction.Paid","data":{"paymentId":"pm-charge-1"}}"""

    @Test
    fun `제대로 서명된 웹훅은 통과한다`() {
        val headers = sign(body, secret)

        assertThat(verifier.verify(body, headers)).isTrue()
    }

    @Test
    fun `다른 시크릿으로 서명하면 거부한다`() {
        val headers = sign(body, "whsec_YW5vdGhlci1zZWNyZXQtdmFsdWU=")

        assertThat(verifier.verify(body, headers)).isFalse()
    }

    @Test
    fun `본문이 한 글자라도 바뀌면 거부한다`() {
        val headers = sign(body, secret)
        val tampered = body.replace("pm-charge-1", "pm-charge-2")

        assertThat(verifier.verify(tampered, headers)).isFalse()
    }

    @Test
    fun `너무 오래된 웹훅은 거부한다`() {
        val old = Instant.now().minusSeconds(60 * 60)
        val headers = sign(body, secret, timestamp = old)

        assertThat(verifier.verify(body, headers)).isFalse()
    }

    @Test
    fun `미래에서 온 웹훅도 거부한다`() {
        val future = Instant.now().plusSeconds(60 * 60)
        val headers = sign(body, secret, timestamp = future)

        assertThat(verifier.verify(body, headers)).isFalse()
    }

    @Test
    fun `허용 오차 안의 시각은 통과한다`() {
        val slightlyOld = Instant.now().minusSeconds(60)
        val headers = sign(body, secret, timestamp = slightlyOld)

        assertThat(verifier.verify(body, headers)).isTrue()
    }

    @Test
    fun `서명 헤더가 없으면 거부한다`() {
        val headers = sign(body, secret) - "webhook-signature"

        assertThat(verifier.verify(body, headers)).isFalse()
    }

    @Test
    fun `id 헤더가 없으면 거부한다`() {
        val headers = sign(body, secret) - "webhook-id"

        assertThat(verifier.verify(body, headers)).isFalse()
    }

    @Test
    fun `서명이 여러 개면 하나만 맞아도 통과한다`() {
        // 시크릿 로테이션 중에는 옛 서명과 새 서명이 함께 온다
        val valid = sign(body, secret)["webhook-signature"]!!
        val headers = sign(body, secret) + ("webhook-signature" to "v1,bm90LWEtcmVhbC1zaWc= $valid")

        assertThat(verifier.verify(body, headers)).isTrue()
    }

    @Test
    fun `시크릿이 설정되지 않았으면 무조건 거부한다`() {
        // 열어두면 시크릿을 잃어버린 순간 아무나 코인을 넣을 수 있다
        val noSecret = PortOneWebhookVerifier(props(""))

        assertThat(noSecret.verify(body, sign(body, secret))).isFalse()
    }

    private fun props(webhookSecret: String) = PortOneProperties(
        baseUrl = "https://api.portone.io",
        storeId = "store-1",
        channelKey = "channel-key-1",
        apiSecret = "secret",
        webhookSecret = webhookSecret,
        timeoutSeconds = 5,
    )

    /** 포트원이 보내는 것과 같은 모양으로 서명한다. */
    private fun sign(
        payload: String,
        secret: String,
        id: String = "msg_test_1",
        timestamp: Instant = Instant.now(),
    ): Map<String, String> {
        val epoch = timestamp.epochSecond
        val key = Base64.getDecoder().decode(secret.removePrefix("whsec_"))
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val signature = Base64.getEncoder()
            .encodeToString(mac.doFinal("$id.$epoch.$payload".toByteArray()))
        return mapOf(
            "webhook-id" to id,
            "webhook-timestamp" to epoch.toString(),
            "webhook-signature" to "v1,$signature",
        )
    }
}
