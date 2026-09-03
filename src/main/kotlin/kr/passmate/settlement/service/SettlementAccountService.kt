package kr.passmate.settlement.service

import kr.passmate.common.security.TextEncryptor
import kr.passmate.settlement.domain.SettlementAccount
import kr.passmate.settlement.dto.SettlementAccountRequest
import kr.passmate.settlement.dto.SettlementAccountResponse
import kr.passmate.settlement.dto.SettlementAccountView
import kr.passmate.settlement.repository.SettlementAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 정산 계좌 등록·조회 (FR-056). 회원당 하나라 upsert 다.
 *
 * 계좌번호는 들어올 때 바로 암호화하고, 나갈 때는 마스킹해서만 보낸다 —
 * 원문이 응답이나 로그에 실릴 자리를 아예 만들지 않는다.
 */
@Service
class SettlementAccountService(
    private val settlementAccountRepository: SettlementAccountRepository,
    private val textEncryptor: TextEncryptor,
) {

    @Transactional
    fun upsert(userId: Long, request: SettlementAccountRequest): SettlementAccountResponse {
        val accountNo = request.accountNo.trim()
        val encrypted = textEncryptor.encrypt(accountNo)

        val account = settlementAccountRepository.findByUserId(userId)
            ?.apply { update(request.bankCode.trim(), request.bankName.trim(), encrypted, request.holderName.trim()) }
            ?: settlementAccountRepository.save(
                SettlementAccount(
                    userId = userId,
                    bankCode = request.bankCode.trim(),
                    bankName = request.bankName.trim(),
                    accountNoEnc = encrypted,
                    holderName = request.holderName.trim(),
                ),
            )
        // 조회와 같은 모양으로 돌려준다 — 클라이언트가 매퍼를 두 벌 들고 있을 이유가 없다
        return SettlementAccountResponse.of(SettlementAccountView.of(account, accountNo))
    }

    @Transactional(readOnly = true)
    fun myAccount(userId: Long): SettlementAccountResponse =
        SettlementAccountResponse.of(settlementAccountRepository.findByUserId(userId)?.let(::toView))

    /** 계좌를 등록해 뒀는지. 정산 배치가 보류 여부를 정할 때 쓴다(FR-056). */
    @Transactional(readOnly = true)
    fun hasAccount(userId: Long): Boolean = settlementAccountRepository.findByUserId(userId) != null

    private fun toView(account: SettlementAccount) =
        SettlementAccountView.of(account, textEncryptor.decrypt(account.accountNoEnc))
}
