package kr.passmate.session.dto

import java.time.LocalDateTime

/**
 * PARTICIPANT_JOINED · PARTICIPANT_LEFT 페이로드.
 * 대기실 명단 항목과 같은 모양(ParticipantResponse)을 유지한다 — 웹이 그대로 명단에 반영한다.
 */
data class ParticipantEventPayload(
    val id: Long,
    val nickname: String,
    val avatarId: String,
    val isGuest: Boolean,
    val joinedAt: LocalDateTime,
)
