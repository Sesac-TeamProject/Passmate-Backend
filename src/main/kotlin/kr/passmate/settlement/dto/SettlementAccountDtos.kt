package kr.passmate.settlement.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kr.passmate.settlement.domain.SettlementAccount
import java.time.LocalDateTime

@Schema(description = "정산 계좌 등록·변경")
data class SettlementAccountRequest(
    @field:Schema(description = "은행 코드", example = "088")
    @field:NotBlank(message = "은행 코드는 필수입니다.")
    @field:Size(max = 10)
    val bankCode: String,

    @field:NotBlank(message = "은행 이름은 필수입니다.")
    @field:Size(max = 50)
    val bankName: String,

    @field:Schema(description = "계좌번호. 숫자만 보낸다(하이픈 없이)")
    @field:NotBlank(message = "계좌번호는 필수입니다.")
    @field:Pattern(regexp = "^[0-9]{8,20}$", message = "계좌번호는 하이픈 없이 숫자 8~20자리로 보내 주세요.")
    val accountNo: String,

    @field:NotBlank(message = "예금주는 필수입니다.")
    @field:Size(max = 50)
    val holderName: String,
)

/**
 * 등록된 계좌. **계좌번호 원문은 담지 않는다** —
 * 정산 화면은 "어느 계좌인지 알아볼 수 있으면" 충분하다.
 */
@Schema(description = "정산 계좌")
data class SettlementAccountView(
    val bankCode: String,
    val bankName: String,
    @field:Schema(description = "뒤 네 자리만 남기고 가린다", example = "********6789")
    val accountNoMasked: String,
    val holderName: String,
    @field:Schema(description = "예금주 실명 확인 여부. 계좌를 바꾸면 다시 false 가 된다")
    val verified: Boolean,
    val verifiedAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun of(account: SettlementAccount, accountNo: String) = SettlementAccountView(
            bankCode = account.bankCode,
            bankName = account.bankName,
            accountNoMasked = mask(accountNo),
            holderName = account.holderName,
            verified = account.verified,
            verifiedAt = account.verifiedAt,
            updatedAt = account.updatedAt,
        )

        /** 뒤 네 자리만 남긴다. 네 자리 이하면 전부 가린다. */
        fun mask(accountNo: String): String {
            if (accountNo.length <= VISIBLE_TAIL) return "*".repeat(accountNo.length)
            return "*".repeat(accountNo.length - VISIBLE_TAIL) + accountNo.takeLast(VISIBLE_TAIL)
        }

        private const val VISIBLE_TAIL = 4
    }
}

@Schema(description = "정산 계좌 조회 — 미등록이면 account 가 빠진다")
data class SettlementAccountResponse(
    val registered: Boolean,
    val account: SettlementAccountView?,
) {
    companion object {
        fun of(account: SettlementAccountView?) =
            SettlementAccountResponse(registered = account != null, account = account)
    }
}
