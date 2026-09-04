package kr.passmate.session.service

import kr.passmate.common.event.ParticipantJoinedEvent
import kr.passmate.common.event.ParticipantLeftEvent
import kr.passmate.session.domain.SessionEventType
import kr.passmate.session.dto.ParticipantEventPayload
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 참가자 입·퇴장을 방 토픽으로 중계한다(웹 QA_BACKLOG B-3).
 * 이벤트 발행처는 room 기능(ParticipantService) — 직접 참조 대신 common/event 로 이어진다.
 */
@Component
class ParticipantBroadcastListener(
    private val eventPublisher: SessionEventPublisher,
) {

    @EventListener
    fun onJoined(event: ParticipantJoinedEvent) {
        eventPublisher.toRoom(
            event.roomId,
            SessionEventType.PARTICIPANT_JOINED,
            ParticipantEventPayload(event.participantId, event.nickname, event.avatarId, event.isGuest, event.joinedAt),
        )
    }

    @EventListener
    fun onLeft(event: ParticipantLeftEvent) {
        eventPublisher.toRoom(
            event.roomId,
            SessionEventType.PARTICIPANT_LEFT,
            ParticipantEventPayload(event.participantId, event.nickname, event.avatarId, event.isGuest, event.joinedAt),
        )
    }
}
