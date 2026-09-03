package kr.passmate.coin.client

import java.time.LocalDateTime

/**
 * 포트원 V2 에서 본 결제 한 건. **포트원이 말한 사실만** 담는다 —
 * 금액이 맞는지 판단하는 것은 서비스의 몫이다.
 */
data class PortOnePayment(
    /** 우리가 발급한 주문 ID(merchantUid). V2 에서는 이것이 곧 paymentId 다 */
    val paymentId: String,
    val status: PortOnePaymentStatus,
    /** 실제로 결제된 총액(원). 우리가 요청한 금액과 대조할 값이다 */
    val totalAmount: Int,
    /** PG 사의 거래 ID. 대사(對査)용으로 남겨 둔다 */
    val pgTxId: String? = null,
    val paidAt: LocalDateTime? = null,
) {
    /** 코인을 넣어도 되는 상태인지. 이것만으로는 부족하고 금액 대조가 함께 필요하다 */
    val isPaid: Boolean get() = status == PortOnePaymentStatus.PAID
}

/**
 * 포트원 결제 상태.
 *
 * [UNKNOWN] 이 있는 이유 — 포트원이 상태값을 늘려도 우리 서버가 500 을 내면 안 된다.
 * 모르는 값은 "확정 아님"으로 떨어지므로 코인이 잘못 들어갈 일도 없다.
 */
enum class PortOnePaymentStatus {
    READY,
    PAID,
    FAILED,
    CANCELLED,
    PARTIAL_CANCELLED,
    PAY_PENDING,
    VIRTUAL_ACCOUNT_ISSUED,
    UNKNOWN,
    ;

    companion object {
        fun from(raw: String?): PortOnePaymentStatus =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: UNKNOWN
    }
}

/**
 * 포트원과 이야기하는 창구. Service 가 HTTP·SDK 를 직접 다루지 않는다.
 * 테스트는 Fake 로 갈아끼운다 — 자동화 테스트가 실제 결제 API 를 부르지 않는다.
 */
interface PortOneClient {

    /** 결제를 시작해도 되는 설정인지. false 면 결제 경로를 502 로 막는다 */
    val isConfigured: Boolean

    /**
     * 결제 단건 조회. **클라이언트가 보낸 금액을 믿지 않고 이걸로 대조한다.**
     * 실패는 [kr.passmate.common.exception.BusinessException] 502 로 번역한다.
     */
    fun getPayment(paymentId: String): PortOnePayment
}
