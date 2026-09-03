package kr.passmate.coin.service

import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.coin.client.PortOneWebhookVerifier
import kr.passmate.coin.repository.CoinChargeRepository
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 포트원 웹훅 처리 (FR-051).
 *
 * 클라이언트의 승인 호출이 끊겨도 **코인 적립을 보장하는** 경로다. 사용자가 결제 직후
 * 창을 닫거나 네트워크가 끊기면 confirm 이 오지 않는데, 그때 돈만 빠져나간 상태로 두면 안 된다.
 *
 * 두 가지를 지킨다.
 * - **서명 검증 실패는 401.** 주소가 공개돼 있어 서명이 유일한 방벽이다
 * - **처리 실패는 200.** 400 을 주면 포트원이 5번까지 재시도한다(최대 256분).
 *   다시 보내도 결과가 같은 실패(모르는 결제·금액 불일치)를 재시도로 돌려받을 이유가 없다
 */
@Service
class PortOneWebhookService(
    private val verifier: PortOneWebhookVerifier,
    private val coinChargeRepository: CoinChargeRepository,
    private val coinChargeService: CoinChargeService,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 서명을 확인한다. 통과하지 못하면 401 로 끊는다. */
    fun verifySignature(body: String, headers: Map<String, String>) {
        if (!verifier.verify(body, headers)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED, "웹훅 서명을 확인하지 못했습니다.")
        }
    }

    /**
     * 웹훅 본문대로 충전 건 상태를 맞춘다.
     *
     * 여기서 던지는 예외는 곧 포트원의 재시도를 부르므로, **재시도로 해결될 일이 아니면
     * 로그만 남기고 조용히 끝낸다.**
     */
    @Transactional
    fun handle(body: String, now: LocalDateTime = LocalDateTime.now()) {
        val node = runCatching { objectMapper.readTree(body) }.getOrNull()
            ?: return log.warn("웹훅 본문을 읽지 못했다")

        val type = node.path("type").asText("")
        val paymentId = node.path("data").path("paymentId").asText(null)
            ?: return log.warn("웹훅에 paymentId 가 없다 type={}", type)

        // 우리가 만든 충전 건이 아니면 할 일이 없다. 남의 상점 웹훅이 잘못 오거나
        // 로컬에서 만든 결제의 웹훅이 운영으로 올 수도 있다
        val charge = coinChargeRepository.findByMerchantUidForUpdate(paymentId)
            ?: return log.info("모르는 결제라 넘어간다 paymentId={} type={}", paymentId, type)

        when {
            type.endsWith(PAID) -> applyPaid(charge.id, paymentId, now)
            type.endsWith(CANCELLED) -> {
                // 코인 회수는 하지 않는다 — 이미 쓴 코인이 있으면 잔액이 음수가 된다.
                // 충전 취소 정책은 미정이고 관리자 환불로 대응한다(API 명세 FR-052 주석)
                charge.markCanceled("포트원 결제 취소 웹훅", now)
                log.warn("충전이 취소됐다. 코인 회수는 수동 처리가 필요하다 chargeId={}", charge.id)
            }
            type.endsWith(FAILED) -> charge.markFailed("포트원 결제 실패 웹훅", now)
            else -> log.debug("처리 대상이 아닌 웹훅 type={}", type)
        }
    }

    /**
     * 확정은 승인 호출과 **같은 경로**를 탄다 — 검증 규칙이 두 벌이면 언젠가 어긋나고,
     * 그 어긋남은 코인이 잘못 들어가는 형태로 나타난다.
     */
    private fun applyPaid(chargeId: Long, paymentId: String, now: LocalDateTime) {
        val charge = coinChargeRepository.findByIdForUpdate(chargeId) ?: return
        try {
            coinChargeService.applyPayment(charge, now)
        } catch (e: BusinessException) {
            // 금액 불일치·미결제는 재시도해도 같은 결과다. 조사할 수 있게 남기고 넘어간다
            log.warn("웹훅으로 확정하지 못했다 paymentId={} code={}", paymentId, e.errorCode.name)
        }
    }

    private companion object {
        const val PAID = "Paid"
        const val CANCELLED = "Cancelled"
        const val FAILED = "Failed"
    }
}
