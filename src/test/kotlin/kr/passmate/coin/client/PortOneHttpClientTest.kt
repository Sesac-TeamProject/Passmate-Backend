package kr.passmate.coin.client

import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestClient

/**
 * 포트원 V2 결제 단건 조회.
 *
 * 이 클라이언트가 하는 일은 **포트원이 말하는 사실을 그대로 옮기는 것**뿐이다 —
 * 금액이 맞는지 판단하는 것은 서비스의 몫이고, 여기서 하면 검증 로직이 HTTP 층에 숨는다.
 */
class PortOneHttpClientTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var client: PortOneHttpClient

    private val properties = PortOneProperties(
        baseUrl = "https://api.portone.io",
        storeId = "store-1",
        channelKey = "channel-key-1",
        apiSecret = "test-secret",
        webhookSecret = "whsec",
        timeoutSeconds = 5,
    )

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        server = MockRestServiceServer.bindTo(builder).build()
        client = PortOneHttpClient(properties, ObjectMapper(), builder.build())
    }

    @Test
    fun `결제를 조회하면 상태와 금액을 준다`() {
        server.expect(requestTo("https://api.portone.io/payments/pm-charge-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(
                    """{"id":"pm-charge-1","status":"PAID","amount":{"total":10000},"pgTxId":"tx-9"}""",
                ),
            )

        val payment = client.getPayment("pm-charge-1")

        assertThat(payment.paymentId).isEqualTo("pm-charge-1")
        assertThat(payment.status).isEqualTo(PortOnePaymentStatus.PAID)
        assertThat(payment.totalAmount).isEqualTo(10_000)
        assertThat(payment.pgTxId).isEqualTo("tx-9")
    }

    @Test
    fun `API Secret 을 인증 헤더로 보낸다`() {
        server.expect(requestTo("https://api.portone.io/payments/pm-charge-1"))
            .andExpect(header("Authorization", "PortOne test-secret"))
            .andRespond(
                withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"id":"pm-charge-1","status":"PAID","amount":{"total":10000}}"""),
            )

        client.getPayment("pm-charge-1")

        server.verify()
    }

    @Test
    fun `아직 결제되지 않은 건도 그대로 옮긴다`() {
        respondWith("""{"id":"pm-charge-1","status":"READY","amount":{"total":10000}}""")

        assertThat(client.getPayment("pm-charge-1").status).isEqualTo(PortOnePaymentStatus.READY)
    }

    @Test
    fun `취소된 건도 그대로 옮긴다`() {
        respondWith("""{"id":"pm-charge-1","status":"CANCELLED","amount":{"total":10000}}""")

        assertThat(client.getPayment("pm-charge-1").status).isEqualTo(PortOnePaymentStatus.CANCELLED)
    }

    @Test
    fun `모르는 상태값이 와도 터지지 않고 UNKNOWN 으로 본다`() {
        // 포트원이 상태를 늘려도 우리 서버가 500 을 내지는 않아야 한다.
        // 모르면 "확정 아님"으로 취급되므로 코인이 잘못 들어갈 일도 없다
        respondWith("""{"id":"pm-charge-1","status":"WHAT_IS_THIS","amount":{"total":10000}}""")

        assertThat(client.getPayment("pm-charge-1").status).isEqualTo(PortOnePaymentStatus.UNKNOWN)
    }

    @Test
    fun `없는 결제를 조회하면 502 로 번역한다`() {
        server.expect(requestTo("https://api.portone.io/payments/nope"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).body("""{"message":"not found"}"""))

        assertThatThrownBy { client.getPayment("nope") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.EXTERNAL_API_ERROR)
    }

    @Test
    fun `포트원이 5xx 를 주면 502 로 번역한다`() {
        server.expect(requestTo("https://api.portone.io/payments/pm-charge-1"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { client.getPayment("pm-charge-1") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.EXTERNAL_API_ERROR)
    }

    @Test
    fun `설정이 비어 있으면 호출하지 않고 502 로 막는다`() {
        val unconfigured = PortOneHttpClient(
            properties.copy(apiSecret = ""), ObjectMapper(), RestClient.builder().build(),
        )

        assertThatThrownBy { unconfigured.getPayment("pm-charge-1") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.EXTERNAL_API_ERROR)
    }

    private fun respondWith(body: String) {
        server.expect(requestTo("https://api.portone.io/payments/pm-charge-1"))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(body))
    }
}
