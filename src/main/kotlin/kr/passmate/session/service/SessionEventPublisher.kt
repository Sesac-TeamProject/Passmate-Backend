package kr.passmate.session.service

import kr.passmate.session.domain.SessionEventType
import kr.passmate.session.dto.SessionEvent
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 세션 이벤트를 STOMP 토픽으로 내보낸다.
 *
 * 방 전체와 호스트 전용 두 갈래로 나눈 이유: 제출 현황·실시간 정답률은 호스트만 봐야 한다.
 * 같은 토픽으로 보내고 클라이언트에서 가리는 방식은 페이로드가 그대로 노출돼 의미가 없다.
 *
 * **트랜잭션 안이면 커밋 후에 보낸다.** 커밋 전에 내보내면 QUESTION_STARTED 를 받은 학생이
 * 곧바로 제출했을 때 서버가 아직 커밋 전 스냅샷을 읽어 QUESTION_NOT_RUNNING(409)이 난다
 * (시나리오 테스트 "시간이 남아 있는데 답을 낼 수 없음", 2026-09-08). 롤백되면 아예 안 보낸다 —
 * 일어나지 않은 상태 변화를 알리지 않는다. 등록 순서대로 발행되므로 이벤트 순서도 유지된다.
 */
@Component
class SessionEventPublisher(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    fun toRoom(roomId: Long, type: SessionEventType, payload: Any? = null) {
        send(roomTopic(roomId), SessionEvent.of(type, roomId, payload))
    }

    fun toHost(roomId: Long, type: SessionEventType, payload: Any? = null) {
        send(hostTopic(roomId), SessionEvent.of(type, roomId, payload))
    }

    private fun send(destination: String, event: SessionEvent<*>) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            messagingTemplate.convertAndSend(destination, event)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    messagingTemplate.convertAndSend(destination, event)
                }
            },
        )
    }

    companion object {
        fun roomTopic(roomId: Long) = "/topic/rooms/$roomId"
        fun hostTopic(roomId: Long) = "/topic/rooms/$roomId/host"
    }
}
