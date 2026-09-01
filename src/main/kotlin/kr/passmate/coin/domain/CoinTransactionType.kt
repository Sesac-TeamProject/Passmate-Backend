package kr.passmate.coin.domain

/**
 * 코인이 움직인 이유. `coin_transaction.type` 과 값이 같다.
 *
 * 부호는 여기서 정한다 — 서비스가 매번 +/- 를 판단하면 원장이 어긋난다.
 */
enum class CoinTransactionType(val sign: Int) {
    /** 충전 (포트원 결제 확정) */
    CHARGE(+1),

    /** 유료 방 참가비 차감 */
    ENTRY(-1),

    /** 환급 — 참가비 취소·AI 분석 실패 */
    REFUND(+1),

    /** 서술형 AI 분석 차감 (무료 한도 초과분) */
    AI_ANALYSIS(-1),

    /** 관리자 수동 조정. 부호는 amount 로 들어온 값을 그대로 쓴다 */
    ADMIN_ADJUST(0),
}

/** 원장 한 줄이 가리키는 대상. `coin_transaction.ref_type` 과 값이 같다. */
enum class CoinRefType {
    COIN_CHARGE,
    ENTRY_PAYMENT,
    AI_FEEDBACK,
}
