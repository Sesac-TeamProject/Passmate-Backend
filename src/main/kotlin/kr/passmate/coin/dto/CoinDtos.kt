package kr.passmate.coin.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransaction
import kr.passmate.coin.domain.CoinTransactionType
import java.time.LocalDateTime

/**
 * 코인 원장 한 줄 (FR-053). 마이페이지 "코인 사용·충전 내역" 이 쓴다.
 *
 * [description] 은 원장의 memo 를 그대로 낸다 — 방 제목·결제 번호는 차감하던
 * **그 시점에** 박아 둔 값이라, 방 제목이 나중에 바뀌어도 영수증은 흔들리지 않는다.
 */
@Schema(description = "코인 내역 한 줄")
data class CoinTransactionRow(
    val id: Long,
    val type: CoinTransactionType,
    @field:Schema(description = "부호 있는 금액. + 충전·환급, - 차감")
    val amount: Int,
    @field:Schema(description = "이 건을 적용한 뒤의 잔액")
    val balanceAfter: Int,
    @field:Schema(description = "가리키는 대상 종류. 영수증으로 이어갈 때 쓴다")
    val refType: CoinRefType?,
    val refId: Long?,
    @field:Schema(description = "내역에 보일 설명. 방 제목·결제 번호가 여기 들어 있다")
    val description: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(tx: CoinTransaction) = CoinTransactionRow(
            id = tx.id,
            type = tx.type,
            amount = tx.amount,
            balanceAfter = tx.balanceAfter,
            refType = tx.refType,
            refId = tx.refId,
            description = tx.memo,
            createdAt = tx.createdAt,
        )
    }
}

/**
 * 내 코인 (FR-050 · FR-053). 마이페이지 코인 카드와
 * 유료 방 입장 화면의 "부족 코인" 계산이 쓴다.
 */
@Schema(description = "내 코인")
data class CoinBalanceResponse(
    @field:Schema(description = "보유 코인. 1 C = ₩1")
    val balance: Int,
    @field:Schema(description = "기본 결제 수단. 설정한 적 없으면 빠진다")
    val defaultPaymentMethod: String?,
    @field:Schema(description = "가장 최근 내역 한 건. 없으면 빠진다")
    val lastTransaction: CoinTransactionRow?,
)
