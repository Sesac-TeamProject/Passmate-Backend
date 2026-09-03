package kr.passmate.coin.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 포트원 V2 REST API 호출.
 *
 * 인증은 `Authorization: PortOne {API Secret}` 이다. 이 시크릿은 결제 조회뿐 아니라
 * **취소까지 되는 열쇠**라 로그에도 응답에도 절대 싣지 않는다.
 */
@Component
class PortOneHttpClient(
    private val properties: PortOneProperties,
    private val objectMapper: ObjectMapper,
    /**
     * base URL·타임아웃이 이미 잡힌 것을 받는다. 여기서 요청 팩토리를 갈아끼우면
     * 테스트가 붙인 목 서버까지 함께 밀어내 실제 포트원으로 요청이 나간다
     */
    private val restClient: RestClient,
) : PortOneClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override val isConfigured: Boolean get() = properties.isConfigured

    override fun getPayment(paymentId: String): PortOnePayment {
        // 설정이 비었는데 호출하면 401 이 돌아온다. 원인을 알아보기 어려운 실패 대신 여기서 막는다
        if (!properties.isConfigured) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "결제 설정이 완료되지 않았습니다.")
        }

        val body = try {
            restClient.get()
                .uri("/payments/{paymentId}", paymentId)
                .header(HttpHeaders.AUTHORIZATION, "PortOne ${properties.apiSecret}")
                .retrieve()
                .body(String::class.java)
        } catch (e: RestClientException) {
            // 예외 메시지에 시크릿이 섞일 일은 없지만, 원인은 로그에만 남기고 응답에는 싣지 않는다
            log.warn("포트원 결제 조회 실패 paymentId={}", paymentId, e)
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "결제 정보를 확인하지 못했습니다.", e)
        } ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "결제 정보를 확인하지 못했습니다.")

        return parse(body, paymentId)
    }

    private fun parse(body: String, paymentId: String): PortOnePayment {
        val node = try {
            objectMapper.readTree(body)
        } catch (e: Exception) {
            log.warn("포트원 응답을 읽지 못함 paymentId={}", paymentId, e)
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "결제 정보를 확인하지 못했습니다.", e)
        }

        return PortOnePayment(
            paymentId = node.path("id").asText(paymentId),
            status = PortOnePaymentStatus.from(node.path("status").asText(null)),
            totalAmount = node.path("amount").path("total").asInt(0),
            pgTxId = node.path("pgTxId").asText(null),
            paidAt = node.path("paidAt").asText(null)?.let(::parseTime),
        )
    }

    /** 포트원은 ISO-8601 오프셋 형식으로 준다. 우리는 UTC 로 저장한다 */
    private fun parseTime(raw: String): LocalDateTime? = try {
        OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
    } catch (e: Exception) {
        log.debug("결제 시각을 읽지 못함 raw={}", raw)
        null
    }

}
