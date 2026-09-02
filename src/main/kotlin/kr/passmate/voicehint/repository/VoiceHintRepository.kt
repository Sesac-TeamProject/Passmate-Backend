package kr.passmate.voicehint.repository

import kr.passmate.voicehint.domain.VoiceHint
import org.springframework.data.jpa.repository.JpaRepository

interface VoiceHintRepository : JpaRepository<VoiceHint, Long> {

    /** 방의 힌트 전부를 내보낸 순서대로. 사용 이력 화면이 그대로 쓴다. */
    fun findAllByRoomIdOrderByPublishedAtAsc(roomId: Long): List<VoiceHint>

    fun findAllByRoomIdAndSessionQuestionIdOrderByPublishedAtAsc(
        roomId: Long,
        sessionQuestionId: Long,
    ): List<VoiceHint>
}
