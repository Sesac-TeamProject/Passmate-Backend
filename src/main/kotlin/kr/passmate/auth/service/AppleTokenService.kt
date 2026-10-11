package kr.passmate.auth.service

import kr.passmate.auth.client.AppleOAuthClient
import kr.passmate.common.event.UserWithdrawnEvent
import kr.passmate.common.security.TextEncryptor
import kr.passmate.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Apple refresh token 의 보관과 폐기.
 *
 * 둘 다 **실패해도 본 흐름을 막지 않는다** — 토큰을 못 받아도 로그인은 되고, 폐기가 실패해도 탈퇴는 유지된다.
 * 실패는 로그로만 남긴다. 키(.p8)가 설정되지 않았으면 아무것도 하지 않는다.
 */
@Service
class AppleTokenService(
    private val appleOAuthClient: AppleOAuthClient,
    private val textEncryptor: TextEncryptor,
    private val userService: UserService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 로그인 직후 앱이 준 인가 코드를 refresh token 으로 바꿔 암호화해 보관한다. 외부 호출이라 트랜잭션 밖에서 부른다 */
    fun linkOnLogin(userId: Long, authorizationCode: String?, clientId: String?) {
        if (authorizationCode.isNullOrBlank() || clientId.isNullOrBlank()) {
            return
        }
        if (!appleOAuthClient.isTokenApiConfigured) {
            log.warn("Apple 토큰 API 키(.p8)가 없어 refresh token 을 보관하지 않는다 — 탈퇴 시 연결 해제가 안 된다 userId={}", userId)
            return
        }
        try {
            val refreshToken = appleOAuthClient.exchangeAuthorizationCode(authorizationCode, clientId)
            userService.linkAppleRefreshToken(userId, textEncryptor.encrypt(refreshToken), clientId)
        } catch (e: Exception) {
            log.warn("Apple refresh token 보관 실패 — 로그인은 계속한다 userId={}", userId, e)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun revokeOnWithdraw(event: UserWithdrawnEvent) {
        val encrypted = event.appleRefreshToken
        val clientId = event.appleClientId
        if (encrypted.isNullOrBlank() || clientId.isNullOrBlank()) {
            return
        }
        try {
            appleOAuthClient.revokeRefreshToken(textEncryptor.decrypt(encrypted), clientId)
            log.info("탈퇴 회원의 Apple 연결을 해제했다 userId={}", event.userId)
        } catch (e: Exception) {
            log.warn("Apple 토큰 폐기 실패 — 탈퇴는 유지된다 userId={}", event.userId, e)
        }
    }
}
