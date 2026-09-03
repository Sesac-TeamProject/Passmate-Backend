package kr.passmate.coin.service

import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransaction
import kr.passmate.coin.domain.CoinTransactionType
import kr.passmate.coin.domain.CoinWallet
import kr.passmate.coin.repository.CoinTransactionRepository
import kr.passmate.coin.repository.CoinWalletRepository
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 코인을 움직이는 유일한 창구. 잔액(coin_wallet)과 원장(coin_transaction)을
 * **한 트랜잭션에서 함께** 바꾼다 — 둘 중 하나만 남으면 원장이 잔액을 설명하지 못한다.
 *
 * 호출자의 트랜잭션에 참여한다(REQUIRED). 차감이 성공했는데 그 뒤 작업이 실패하면
 * 차감도 같이 롤백되는 게 맞다.
 */
@Service
class CoinService(
    private val coinWalletRepository: CoinWalletRepository,
    private val coinTransactionRepository: CoinTransactionRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 차감. 잔액이 모자라면 402 로 막는다.
     * 지갑이 아예 없는 회원도 잔액 0 이라 마찬가지로 402 다 — 조회하다가 지갑을 만들지 않는다.
     */
    @Transactional
    fun deduct(
        userId: Long,
        amount: Int,
        type: CoinTransactionType,
        refType: CoinRefType,
        refId: Long,
        memo: String? = null,
    ): CoinTransaction {
        require(amount > 0) { "차감 금액은 0보다 커야 합니다." }
        val wallet = coinWalletRepository.findByUserIdForUpdate(userId)
            ?: throw BusinessException(
                ErrorCode.INSUFFICIENT_COINS,
                data = CoinWallet.shortfallOf(required = amount, balance = 0),
            )

        wallet.deduct(amount)
        return record(userId, type, -amount, wallet.balance, refType, refId, memo)
    }

    /**
     * 환급. 같은 대상에 REFUND 가 이미 있으면 아무것도 하지 않는다(멱등) —
     * 분석 실패 콜백이 두 번 들어와도 코인을 두 번 돌려주지 않는다.
     */
    @Transactional
    fun refund(
        userId: Long,
        amount: Int,
        refType: CoinRefType,
        refId: Long,
        memo: String? = null,
    ): CoinTransaction? {
        require(amount > 0) { "환급 금액은 0보다 커야 합니다." }
        if (coinTransactionRepository.existsByRefTypeAndRefIdAndType(refType, refId, CoinTransactionType.REFUND)) {
            log.info("이미 환급된 건이라 건너뛴다 refType={} refId={}", refType, refId)
            return null
        }

        val wallet = coinWalletRepository.findByUserIdForUpdate(userId)
            ?: coinWalletRepository.save(CoinWallet(userId = userId))

        wallet.charge(amount)
        return record(userId, CoinTransactionType.REFUND, amount, wallet.balance, refType, refId, memo)
    }

    /**
     * 남은 코인을 전부 소멸시킨다(회원 탈퇴). 원장은 append-only 라 지우지 않고
     * 반대 부호 한 줄을 쌓아 0 으로 만든다 — 나중에 "코인이 어디로 갔나"를 되짚을 수 있어야 한다.
     * 잔액이 0 이면 아무것도 하지 않는다.
     */
    @Transactional
    fun forfeitAll(userId: Long, memo: String): CoinTransaction? {
        val wallet = coinWalletRepository.findByUserIdForUpdate(userId) ?: return null
        val amount = wallet.balance
        if (amount <= 0) return null

        wallet.deduct(amount)
        return record(userId, CoinTransactionType.ADMIN_ADJUST, -amount, wallet.balance, null, null, memo)
    }

    private fun record(
        userId: Long,
        type: CoinTransactionType,
        signedAmount: Int,
        balanceAfter: Int,
        refType: CoinRefType?,
        refId: Long?,
        memo: String?,
    ): CoinTransaction = coinTransactionRepository.save(
        CoinTransaction(
            userId = userId,
            type = type,
            amount = signedAmount,
            balanceAfter = balanceAfter,
            refType = refType,
            refId = refId,
            memo = memo?.take(CoinTransaction.MEMO_MAX),
        ),
    )
}
