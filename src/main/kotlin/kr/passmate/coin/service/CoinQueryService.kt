package kr.passmate.coin.service

import kr.passmate.coin.dto.CoinBalanceResponse
import kr.passmate.coin.dto.CoinTransactionRow
import kr.passmate.coin.repository.CoinTransactionRepository
import kr.passmate.coin.repository.CoinWalletRepository
import kr.passmate.common.dto.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 코인 조회 전용. **지갑을 만들지 않는다** — 쓰기는 로그인·충전 경로에서만 한다.
 *
 * 방 제목·결제 번호를 붙이려고 room 을 참조하지 않는다. 그 정보는 차감 시점에
 * 원장 memo 로 들어와 있다 — 참조를 만들면 room ⇄ coin 순환이 된다.
 */
@Service
@Transactional(readOnly = true)
class CoinQueryService(
    private val coinWalletRepository: CoinWalletRepository,
    private val coinTransactionRepository: CoinTransactionRepository,
) {

    /** 마이페이지 코인 카드 — 잔액·기본 결제 수단·최근 내역 1건. */
    fun myCoins(userId: Long): CoinBalanceResponse {
        val wallet = coinWalletRepository.findByUserId(userId)
        val last = coinTransactionRepository.findFirstByUserIdOrderByIdDesc(userId)
        return CoinBalanceResponse(
            balance = wallet?.balance ?: 0,
            defaultPaymentMethod = wallet?.defaultPaymentMethod,
            lastTransaction = last?.let(CoinTransactionRow::from),
        )
    }

    /** 충전·차감·환급 내역, 최근 순 페이징. 건별 잔액이 붙는다. */
    fun myTransactions(userId: Long, page: Int, size: Int): PageResponse<CoinTransactionRow> {
        val found = coinTransactionRepository
            .findAllByUserId(userId, PageRequest.of(page, size))
        return PageResponse.from(found, CoinTransactionRow::from)
    }
}
