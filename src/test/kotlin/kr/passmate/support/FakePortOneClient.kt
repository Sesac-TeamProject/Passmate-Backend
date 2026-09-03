package kr.passmate.support

import kr.passmate.coin.client.PortOneClient
import kr.passmate.coin.client.PortOnePayment
import kr.passmate.coin.client.PortOnePaymentStatus
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import java.time.LocalDateTime

/**
 * 포트원 대역. **자동화 테스트가 실제 결제 API 를 부르지 않게** 하는 장치다.
 *
 * 기본값은 "요청한 금액 그대로 결제 완료" — 정상 경로가 대부분이라 그렇게 두고,
 * 금액 불일치·미결제·조회 실패는 테스트가 [stub] 으로 직접 심는다.
 */
class FakePortOneClient : PortOneClient {

    override val isConfigured: Boolean = true

    /** paymentId → 그 결제를 조회했을 때 돌려줄 것. 없으면 [defaultAmount] 로 만든다 */
    private val stubs = mutableMapOf<String, () -> PortOnePayment>()

    /** 심어 두지 않은 결제를 조회했을 때 쓸 금액 */
    var defaultAmount: Int = 0

    fun reset() {
        stubs.clear()
        defaultAmount = 0
    }

    /** 이 결제를 조회하면 이렇게 답하라고 심는다. */
    fun stub(
        paymentId: String,
        status: PortOnePaymentStatus = PortOnePaymentStatus.PAID,
        totalAmount: Int = defaultAmount,
    ) {
        stubs[paymentId] = {
            PortOnePayment(
                paymentId = paymentId,
                status = status,
                totalAmount = totalAmount,
                pgTxId = "fake-tx-$paymentId",
                paidAt = LocalDateTime.now(),
            )
        }
    }

    /** 조회 자체가 실패하는 상황(포트원 장애·없는 결제)을 심는다. */
    fun stubFailure(paymentId: String) {
        stubs[paymentId] = {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "결제 정보를 확인하지 못했습니다.")
        }
    }

    override fun getPayment(paymentId: String): PortOnePayment =
        stubs[paymentId]?.invoke() ?: PortOnePayment(
            paymentId = paymentId,
            status = PortOnePaymentStatus.PAID,
            totalAmount = defaultAmount,
            pgTxId = "fake-tx-$paymentId",
            paidAt = LocalDateTime.now(),
        )
}

@TestConfiguration
class FakePortOneConfig {

    @Bean
    @Primary
    fun fakePortOneClient(): FakePortOneClient = FakePortOneClient()
}
