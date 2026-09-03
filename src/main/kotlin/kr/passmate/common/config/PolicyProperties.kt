package kr.passmate.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 정책값은 전부 여기로 모은다. 서비스 코드에 숫자를 하드코딩하지 않는다.
 * 값은 application.yml 의 passmate.policy.* → 운영은 환경변수로 덮어쓴다.
 */
@ConfigurationProperties(prefix = "passmate.policy")
data class PolicyProperties(
    /** 참가비 하한 (코인, 1 C = 1원) */
    val entryFeeMin: Int,
    /** 참가비 상한 */
    val entryFeeMax: Int,
    /** 코인 충전 1회 하한 (코인, 1 C = 1원) */
    val chargeAmountMin: Int,
    /** 코인 충전 1회 상한. 오입력·악용으로 한 번에 큰 금액이 결제되는 것을 막는다 */
    val chargeAmountMax: Int,
    /** 최소 정산 신청 금액 (원) */
    val settlementMinAmount: Int,
    /**
     * AI 문항 생성 무료 횟수 (호스트, 누적). 명세 "최초 5회 무료(이후 코인 정책 적용 예정)".
     * ai_generation_log 에서 kind=SET·status=SUCCESS 를 세므로 실패는 횟수를 깎지 않는다.
     */
    val aiFreeLimit: Int,
    /** 월 서술형 AI 분석 무료 횟수 (학생, FR-075) */
    val essayAnalysisFreeLimit: Int,
    /** 무료 한도를 넘겼을 때 분석 1건당 차감할 코인 (1 C = 1원) */
    val essayAnalysisCoinCost: Int,
    /** 세션 종료 후 별점·평가 가능 시간 */
    val ratingWindowHours: Long,
    /** 호스트 수익 배분율 (0.8 = 80:20) */
    val hostEarningRate: Double,
    /** 게스트 기록 보관 일수 (지나면 GuestPurgeJob 이 파기) */
    val guestRetentionDays: Long,
)
