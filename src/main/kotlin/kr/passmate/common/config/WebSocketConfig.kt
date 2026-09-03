package kr.passmate.common.config

import kr.passmate.common.security.CorsProperties
import kr.passmate.common.security.StompAuthChannelInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * 실시간 전파용 STOMP 설정.
 *
 * 브로커는 **인메모리 simple broker** 다 — Redis 후순위 결정과 무관하게 동작한다.
 * 다만 인스턴스가 여러 대가 되면 브로커가 인스턴스마다 따로 놀아서 다른 인스턴스에 붙은
 * 참가자에게 전파되지 않는다. 지금은 EC2 1대라 문제없고, 확장 시 외부 브로커로 바꾼다.
 *
 * 클라이언트가 이 채널로 **상태를 바꾸는 경로는 만들지 않는다**.
 * 제어는 전부 REST 로 받고, 서버가 상태를 바꾼 뒤 결과만 여기로 흘려보낸다.
 * 그래서 @MessageMapping 을 쓰는 컨트롤러가 없고 setApplicationDestinationPrefixes 도 두지 않는다.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
    private val corsProperties: CorsProperties,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // 허용 출처는 REST CORS 와 같은 설정(WEB_BASE_URL 기반)을 쓴다 — 도메인을 코드에 박으면
        // CORS 만 갱신되고 WS 는 옛 도메인에 묶이는 사고가 난다(실제로 있었다).
        // Origin 헤더가 없는 요청(네이티브 앱)은 이 검사를 타지 않는다.
        registry.addEndpoint(ENDPOINT)
            .setAllowedOriginPatterns(*corsProperties.originPatterns.toTypedArray())
        // SockJS 폴백은 두지 않는다. 웹·앱 모두 네이티브 WebSocket 을 쓰고,
        // 폴백을 켜면 경로(/ws/info, /ws/**/xhr_streaming)가 늘어 인가 검증 면이 넓어진다.
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 방 전체 /topic/rooms/{roomId}, 호스트 전용 /topic/rooms/{roomId}/host
        registry.enableSimpleBroker("/topic", "/queue")
        // 개인 큐 /user/queue/* — Spring 이 세션별로 라우팅한다
        registry.setUserDestinationPrefix("/user")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        // 인증·구독 인가는 메시지가 브로커에 닿기 전에 끝낸다
        registration.interceptors(stompAuthChannelInterceptor)
    }

    companion object {
        const val ENDPOINT = "/ws"
    }
}
