package kr.passmate.common.event

import java.time.LocalDateTime

/**
 * 참가자가 방에 들어왔다.
 *
 * room 이 session 의 브로드캐스터를 직접 부르면 session → room 참조와 맞물려
 * 순환 참조가 되므로 이 이벤트로 끊는다(§아키텍처 "순환 참조가 생기면 common/event 로").
 * 대기실 명단이 3초 폴링 없이 실시간으로 맞춰지는 근거다(웹 QA_BACKLOG B-3).
 */
data class ParticipantJoinedEvent(
    val roomId: Long,
    val participantId: Long,
    val nickname: String,
    val avatarId: String,
    val isGuest: Boolean,
    val joinedAt: LocalDateTime,
)

/** 참가자가 나갔다. 본인 퇴장과 강퇴 모두 LEFT 하나 — 화면은 명단에서 지우기만 한다. */
data class ParticipantLeftEvent(
    val roomId: Long,
    val participantId: Long,
    val nickname: String,
    val avatarId: String,
    val isGuest: Boolean,
    val joinedAt: LocalDateTime,
)
