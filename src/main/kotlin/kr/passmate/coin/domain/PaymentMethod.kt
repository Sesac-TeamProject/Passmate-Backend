package kr.passmate.coin.domain

/**
 * 코인 충전에 쓸 결제 수단 (FR-053). `coin_wallet.default_payment_method` 와 값이 같다.
 *
 * **카드 번호 같은 결제 정보는 우리가 들고 있지 않다** — 어떤 수단을 기본으로 고를지만
 * 기억한다. 실제 결제 수단 정보는 전부 포트원 쪽에 있다.
 */
enum class PaymentMethod {
    KAKAOPAY,
    NAVERPAY,
    TOSSPAY,

    /** 신용·체크카드 */
    CARD,

    /** 계좌이체 */
    BANK_TRANSFER,
}
