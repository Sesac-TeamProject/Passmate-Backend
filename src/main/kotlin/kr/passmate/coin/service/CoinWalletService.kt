package kr.passmate.coin.service

import kr.passmate.coin.domain.CoinWallet
import kr.passmate.coin.domain.PaymentMethod
import kr.passmate.coin.dto.PaymentMethodResponse
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

    /**
     * 보유 코인. 지갑이 없으면 0 으로 본다 —
     * 조회하다가 지갑을 만들지 않는다(쓰기는 로그인·충전 경로에서만).
     */
    @Transactional(readOnly = true)
    fun getBalance(userId: Long): Int =
        coinWalletRepository.findByUserId(userId)?.balance ?: 0

    /**
     * 기본 결제 수단을 정한다 (FR-053). 지갑은 첫 로그인에 만들어지지만,
     * 없더라도 여기서 만들어 준다 — 설정을 저장할 곳이 없어 실패하는 게 더 나쁘다.
     */
    @Transactional
    fun changePaymentMethod(userId: Long, method: PaymentMethod): PaymentMethodResponse {
        val wallet = createFor(userId)
        wallet.changeDefaultPaymentMethod(method)
        return PaymentMethodResponse(method)
    }
}
