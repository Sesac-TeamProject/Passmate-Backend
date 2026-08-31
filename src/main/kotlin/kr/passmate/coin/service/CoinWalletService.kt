package kr.passmate.coin.service

import kr.passmate.coin.domain.CoinWallet
import kr.passmate.coin.repository.CoinWalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CoinWalletService(
    private val coinWalletRepository: CoinWalletRepository,
) {

    /**
     * 첫 로그인 시 지갑을 만든다. 이미 있으면 그대로 둔다(멱등).
     * 호출자의 트랜잭션에 참여한다 — 가입이 실패하면 지갑도 함께 롤백된다.
     */
    @Transactional
    fun createFor(userId: Long): CoinWallet =
        coinWalletRepository.findByUserId(userId)
            ?: coinWalletRepository.save(CoinWallet(userId = userId))
}
