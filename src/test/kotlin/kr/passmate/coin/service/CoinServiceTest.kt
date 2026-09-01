package kr.passmate.coin.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransaction
import kr.passmate.coin.domain.CoinTransactionType
import kr.passmate.coin.domain.CoinWallet
import kr.passmate.coin.repository.CoinTransactionRepository
import kr.passmate.coin.repository.CoinWalletRepository
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 원장과 잔액은 한 몸이다 — 이 테스트가 지키는 건 "원장 합계 = 지갑 잔액" 하나다.
 */
class CoinServiceTest {

    private val walletRepository = mockk<CoinWalletRepository>()
    private val transactionRepository = mockk<CoinTransactionRepository>()
    private val coinService = CoinService(walletRepository, transactionRepository)

    private val saved = slot<CoinTransaction>()

    private fun walletWith(balance: Int): CoinWallet =
        CoinWallet(userId = USER_ID).apply { charge(balance) }

    private fun captureSave() {
        every { transactionRepository.save(capture(saved)) } answers { saved.captured }
    }

    @Test
    fun `차감하면 잔액이 줄고 원장에 음수로 남는다`() {
        val wallet = walletWith(1_000)
        every { walletRepository.findByUserIdForUpdate(USER_ID) } returns wallet
        captureSave()

        coinService.deduct(USER_ID, 100, CoinTransactionType.AI_ANALYSIS, CoinRefType.AI_FEEDBACK, refId = 7)

        assertThat(wallet.balance).isEqualTo(900)
        assertThat(saved.captured.amount).isEqualTo(-100)
        // 원장 한 줄만 봐도 그 시점 잔액을 알 수 있어야 한다
        assertThat(saved.captured.balanceAfter).isEqualTo(wallet.balance)
        assertThat(saved.captured.refType).isEqualTo(CoinRefType.AI_FEEDBACK)
        assertThat(saved.captured.refId).isEqualTo(7)
    }

    @Test
    fun `잔액이 모자라면 402 로 막고 원장에 아무것도 남기지 않는다`() {
        val wallet = walletWith(50)
        every { walletRepository.findByUserIdForUpdate(USER_ID) } returns wallet

        assertThatThrownBy {
            coinService.deduct(USER_ID, 100, CoinTransactionType.AI_ANALYSIS, CoinRefType.AI_FEEDBACK, refId = 7)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INSUFFICIENT_COINS)

        assertThat(wallet.balance).isEqualTo(50)
        verify(exactly = 0) { transactionRepository.save(any()) }
    }

    @Test
    fun `지갑이 없는 회원도 잔액 0 으로 보고 402 로 막는다`() {
        every { walletRepository.findByUserIdForUpdate(USER_ID) } returns null

        assertThatThrownBy {
            coinService.deduct(USER_ID, 1, CoinTransactionType.AI_ANALYSIS, CoinRefType.AI_FEEDBACK, refId = 7)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INSUFFICIENT_COINS)

        verify(exactly = 0) { walletRepository.save(any()) }
    }

    @Test
    fun `환급하면 잔액이 돌아오고 원장에 양수로 남는다`() {
        val wallet = walletWith(900)
        every { walletRepository.findByUserIdForUpdate(USER_ID) } returns wallet
        every {
            transactionRepository.existsByRefTypeAndRefIdAndType(CoinRefType.AI_FEEDBACK, 7, CoinTransactionType.REFUND)
        } returns false
        captureSave()

        coinService.refund(USER_ID, 100, CoinRefType.AI_FEEDBACK, refId = 7)

        assertThat(wallet.balance).isEqualTo(1_000)
        assertThat(saved.captured.type).isEqualTo(CoinTransactionType.REFUND)
        assertThat(saved.captured.amount).isEqualTo(100)
        assertThat(saved.captured.balanceAfter).isEqualTo(1_000)
    }

    @Test
    fun `같은 건을 두 번 환급하지 않는다`() {
        every {
            transactionRepository.existsByRefTypeAndRefIdAndType(CoinRefType.AI_FEEDBACK, 7, CoinTransactionType.REFUND)
        } returns true

        val result = coinService.refund(USER_ID, 100, CoinRefType.AI_FEEDBACK, refId = 7)

        assertThat(result).isNull()
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { walletRepository.findByUserIdForUpdate(any()) }
    }

    private companion object {
        const val USER_ID = 1L
    }
}
