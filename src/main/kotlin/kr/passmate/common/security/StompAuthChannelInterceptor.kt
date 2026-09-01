package kr.passmate.common.security

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component

/**
 * STOMP 프레임을 가로채 인증·인가를 판정한다.
 *
 * - **CONNECT**: Authorization 헤더의 JWT 를 검증해 주체를 세션에 붙인다.
 *   한 번 붙이면 이후 같은 WebSocket 세션의 모든 프레임에서 그대로 쓸 수 있다.
 * - **SUBSCRIBE**: 남의 방 토픽을 구독하지 못하게 막는다. 이걸 빼면 방 id 만 바꿔서
 *   다른 방의 문제·정답·랭킹을 전부 훔쳐볼 수 있다.
 *
 * 예외를 던지면 클라이언트는 STOMP ERROR 프레임을 받고 연결이 끊긴다.
 */
@Component
class StompAuthChannelInterceptor(
    private val jwtTokenProvider: JwtTokenProvider,
    private val subscriptionAuthorizer: StompSubscriptionAuthorizer,
) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE -> authorizeSubscription(accessor)
            else -> Unit
        }
        return message
    }

    private fun authenticate(accessor: StompHeaderAccessor) {
        val token = accessor.getFirstNativeHeader(HEADER)
            ?.takeIf { it.startsWith(PREFIX, ignoreCase = true) }
            ?.substring(PREFIX.length)
            ?.trim()
            ?: throw MessagingAuthException("인증 토큰이 없습니다.")

        // 만료·위조는 여기서 그대로 튄다 — 연결 자체를 세우지 않는다
        val principal = jwtTokenProvider.parseAuthToken(token)
        accessor.user = UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        accessor.sessionAttributes?.put(SESSION_PRINCIPAL, principal)
    }

    private fun authorizeSubscription(accessor: StompHeaderAccessor) {
        val principal = (accessor.user as? UsernamePasswordAuthenticationToken)?.principal as? AuthPrincipal
            ?: throw MessagingAuthException("인증되지 않은 연결입니다.")
        val destination = accessor.destination
            ?: throw MessagingAuthException("구독 대상이 없습니다.")

        if (!subscriptionAuthorizer.canSubscribe(principal, destination)) {
            log.warn("구독 거부 — principal={} destination={}", principal, destination)
            throw MessagingAuthException("구독 권한이 없습니다: $destination")
        }
    }

    companion object {
        private const val HEADER = "Authorization"
        private const val PREFIX = "Bearer "
        const val SESSION_PRINCIPAL = "passmate.principal"
    }
}

/** STOMP 경로의 인증·인가 실패. 클라이언트에는 ERROR 프레임으로 전달된다. */
class MessagingAuthException(message: String) : RuntimeException(message)
