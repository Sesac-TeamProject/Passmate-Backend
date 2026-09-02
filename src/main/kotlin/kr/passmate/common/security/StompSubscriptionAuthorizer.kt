package kr.passmate.common.security

/**
 * STOMP 구독 인가. 어떤 주체가 어떤 토픽을 구독할 수 있는지 판정한다.
 *
 * common 은 기능 패키지를 알지 못하므로 여기서는 **판정 규약만** 정의하고,
 * 방·참가자를 아는 room 기능이 구현체를 제공한다(포트-어댑터).
 */
interface StompSubscriptionAuthorizer {

    /** 구독 가능하면 true. 판단할 수 없는 목적지는 false 로 막는다(기본 거부). */
    fun canSubscribe(principal: AuthPrincipal, destination: String): Boolean
}
