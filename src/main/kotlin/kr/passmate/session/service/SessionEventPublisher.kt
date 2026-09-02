package kr.passmate.session.service

import kr.passmate.session.domain.SessionEventType
import kr.passmate.session.dto.SessionEvent
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * 세션 이벤트를 STOMP 토픽으로 내보낸다.
 *
 * 방 전체와 호스트 전용 두 갈래로 나눈 이유: 제출 현황·실시간 정답률은 호스트만 봐야 한다.
 * 같은 토픽으로 보내고 클라이언트에서 가리는 방식은 페이로드가 그대로 노출돼 의미가 없다.
 */
@Component
class SessionEventPublisher(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    fun toRoom(roomId: Long, type: SessionEventType, payload: Any? = null) {
        messagingTemplate.convertAndSend(roomTopic(roomId), SessionEvent.of(type, roomId, payload))
    }

    fun toHost(roomId: Long, type: SessionEventType, payload: Any? = null) {
        messagingTemplate.convertAndSend(hostTopic(roomId), SessionEvent.of(type, roomId, payload))
    }

    companion object {
        fun roomTopic(roomId: Long) = "/topic/rooms/$roomId"
        fun hostTopic(roomId: Long) = "/topic/rooms/$roomId/host"
    }
}
