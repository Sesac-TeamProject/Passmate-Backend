package kr.passmate.coin.client

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * 포트원 웹훅 서명 검증 (Standard Webhooks 규격).
 *
 * **이 검증이 웹훅 경로의 유일한 방벽이다.** 주소는 공개돼 있어서, 서명을 안 보면
 * 누구나 "결제 완료" 를 보내 코인을 받아 갈 수 있다.
 *
 * 서명 대상은 `{webhook-id}.{webhook-timestamp}.{본문}` 이고, 키는 `whsec_` 뒤의
 * base64 를 푼 바이트다. 헤더에는 서명이 여러 개 실릴 수 있다 — 시크릿을 무중단으로
 * 교체하는 동안 옛 것과 새 것이 함께 오기 때문이다.
 */
@Component
class PortOneWebhookVerifier(
    private val properties: PortOneProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이 웹훅을 믿어도 되는지. 어떤 이유로든 확신이 안 서면 **false** 다 —
     * 애매할 때 통과시키면 그 순간 방벽이 사라진다.
     */
    fun verify(body: String, headers: Map<String, String>): Boolean {
        if (!properties.isWebhookConfigured) {
            log.warn("웹훅 시크릿이 설정되지 않아 검증할 수 없다")
            return false
        }

        val id = headers[HEADER_ID] ?: return reject("webhook-id 없음")
        val rawTimestamp = headers[HEADER_TIMESTAMP] ?: return reject("webhook-timestamp 없음")
        val signatures = headers[HEADER_SIGNATURE] ?: return reject("webhook-signature 없음")

        val timestamp = rawTimestamp.toLongOrNull() ?: return reject("timestamp 형식 오류")
        // 지난 요청을 그대로 다시 보내는 재전송 공격을 막는다. 미래 시각도 마찬가지로 거부한다
        if (abs(Instant.now().epochSecond - timestamp) > TOLERANCE.seconds) {
            return reject("허용 오차를 벗어난 시각 timestamp=$timestamp")
        }

        val expected = sign("$id.$timestamp.$body") ?: return reject("서명 계산 실패")

        // 로테이션 중에는 여러 서명이 공백으로 이어져 온다. 하나라도 맞으면 통과다
        val matched = signatures.split(" ")
            .mapNotNull { it.substringAfter("$VERSION,", missingDelimiterValue = "").takeIf(String::isNotBlank) }
            .any { constantTimeEquals(it, expected) }

        return matched || reject("일치하는 서명 없음")
    }

    private fun sign(content: String): String? = try {
        val key = Base64.getDecoder().decode(properties.webhookSecret.removePrefix(SECRET_PREFIX))
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }
        Base64.getEncoder().encodeToString(mac.doFinal(content.toByteArray()))
    } catch (e: Exception) {
        log.warn("웹훅 서명을 계산하지 못했다", e)
        null
    }

    /** 앞자리부터 비교하면 걸리는 시간으로 정답을 좁힐 수 있다. 길이와 무관하게 같은 시간을 쓴다 */
    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun reject(reason: String): Boolean {
        // 본문·서명은 남기지 않는다. 이유만 있으면 원인 파악에 충분하다
        log.warn("웹훅 서명 검증 실패 — {}", reason)
        return false
    }

    private companion object {
        const val HEADER_ID = "webhook-id"
        const val HEADER_TIMESTAMP = "webhook-timestamp"
        const val HEADER_SIGNATURE = "webhook-signature"
        const val SECRET_PREFIX = "whsec_"
        const val VERSION = "v1"
        const val ALGORITHM = "HmacSHA256"

        /** 재전송 허용 오차. 포트원 재시도가 최대 256분 뒤라 그건 새 서명으로 온다 */
        val TOLERANCE: Duration = Duration.ofMinutes(5)
    }
}
