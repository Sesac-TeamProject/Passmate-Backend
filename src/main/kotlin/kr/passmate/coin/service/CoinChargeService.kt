package kr.passmate.coin.service

import kr.passmate.coin.client.PortOneClient
import kr.passmate.coin.domain.CoinCharge
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransactionType
import kr.passmate.coin.dto.CoinChargeConfirmResponse
import kr.passmate.coin.dto.CoinChargeRequest
import kr.passmate.coin.dto.CoinChargeResponse
import kr.passmate.coin.repository.CoinChargeRepository
import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.coin.client.PortOneProperties
import kr.passmate.room.service.EntryPaymentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.NumberFormat
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

/**
 * 코인 충전 (FR-050 · FR-051).
 *
 * 확정은 **두 경로로 들어온다** — 사용자의 승인 호출과 포트원 웹훅. 어느 쪽이 먼저 와도 되고
 * 둘 다 와도 되지만 코인은 한 번만 들어간다. 그 보증은 [CoinCharge.markPaid] 가 처음
 * 확정한 호출에만 true 를 주는 것과, 확정 전에 거는 비관적 락 두 가지로 이뤄진다.
 *
 * **클라이언트가 보낸 금액은 믿지 않는다.** 포트원 조회 API 로 대조하고 나서야 코인을 넣는다.
 */
@Service
class CoinChargeService(
    private val coinChargeRepository: CoinChargeRepository,
    private val coinService: CoinService,
    private val coinWalletService: CoinWalletService,
    private val portOneClient: PortOneClient,
    private val portOneProperties: PortOneProperties,
    private val policyProperties: PolicyProperties,
    private val entryPaymentService: EntryPaymentService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 충전 건을 만들고 결제창 파라미터를 준다. **아직 돈은 오가지 않는다** — 상태는 READY 다.
     *
     * 포트원을 부르지 않는다. 결제창은 클라이언트가 이 값으로 직접 띄운다.
     */
    @Transactional
    fun request(userId: Long, request: CoinChargeRequest): CoinChargeResponse {
        if (!portOneClient.isConfigured) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "결제 설정이 완료되지 않았습니다.")
        }
        val amount = verifyAmount(request.amount)

        val charge = coinChargeRepository.save(
            CoinCharge.of(
                userId = userId,
                roomId = request.roomId,
                amount = amount,
                method = request.method,
                merchantUid = issueMerchantUid(),
            ),
        )
        return CoinChargeResponse.of(
            charge = charge,
            storeId = portOneProperties.storeId,
            channelKey = portOneProperties.channelKey,
            orderName = orderNameOf(amount),
        )
    }

    /**
     * 결제 완료 후 서버 검증 (SC-011). 포트원에 실제 상태·금액을 물어보고 일치할 때만 코인을 넣는다.
     *
     * roomId 를 함께 요청했다면 참가비 차감까지 이어서 처리한다("충전 → 차감하고 입장" 원스텝).
     */
    @Transactional
    fun confirm(chargeId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): CoinChargeConfirmResponse {
        val charge = coinChargeRepository.findByIdForUpdate(chargeId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "충전 건을 찾을 수 없습니다.")
        charge.verifyOwner(userId)

        val applied = applyPayment(charge, now)
        // 이미 확정된 건을 다시 눌러도 오류로 답하지 않는다 — 사용자에게는 성공한 결제다
        if (!applied) log.info("이미 확정된 충전이라 코인을 다시 넣지 않는다 chargeId={}", chargeId)

        val entry = charge.roomId
            ?.takeIf { applied }
            ?.let { entryPaymentService.pay(it, userId, now) }

        return CoinChargeConfirmResponse(
            chargeId = charge.id,
            status = charge.status,
            amount = charge.amount,
            balanceAfter = entry?.balanceAfter ?: coinWalletService.getBalance(userId),
            paidAt = charge.paidAt,
            entryPayment = entry,
        )
    }

    /**
     * 포트원에 물어보고 확정한다. 웹훅도 이 경로를 탄다 — 검증 규칙이 두 벌이면 어긋난다.
     *
     * @return 이번 호출이 처음 확정한 것이면 true
     */
    @Transactional
    fun applyPayment(charge: CoinCharge, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val payment = portOneClient.getPayment(charge.merchantUid)

        if (!payment.isPaid) {
            throw BusinessException(ErrorCode.PAYMENT_NOT_COMPLETED)
        }
        // 클라이언트가 보낸 금액이 아니라 **포트원이 말하는 금액**과 대조한다
        if (payment.totalAmount != charge.amount) {
            log.warn(
                "결제 금액 불일치 chargeId={} 요청={} 실제={}",
                charge.id, charge.amount, payment.totalAmount,
            )
            throw BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)
        }

        val first = charge.markPaid(payment.pgTxId ?: payment.paymentId, now)
        if (first) {
            coinService.charge(
                userId = charge.userId,
                amount = charge.amount,
                refType = CoinRefType.COIN_CHARGE,
                refId = charge.id,
                memo = "코인 충전 · ${charge.merchantUid}",
            )
        }
        return first
    }

    private fun verifyAmount(raw: Int?): Int {
        val amount = raw ?: throw BusinessException(ErrorCode.INVALID_INPUT, "충전 금액은 필수입니다.")
        val min = policyProperties.chargeAmountMin
        val max = policyProperties.chargeAmountMax
        if (amount !in min..max) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "충전 금액은 $min~$max 코인 사이여야 합니다.")
        }
        return amount
    }

    /**
     * 우리가 발급하는 주문 ID. 포트원 V2 의 `paymentId` 로 그대로 나간다.
     * 추측할 수 없어야 해서 UUID 를 쓴다 — 순번이면 남의 결제 번호를 찍어볼 수 있다.
     */
    private fun issueMerchantUid(): String = "pm-charge-${UUID.randomUUID().toString().replace("-", "")}"

    private fun orderNameOf(amount: Int): String =
        "패스메이트 코인 ${NumberFormat.getInstance(Locale.KOREA).format(amount)} C 충전"
}
