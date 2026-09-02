package kr.passmate.voicehint.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity
import java.time.LocalDateTime

/**
 * 호스트가 문항 진행 중 내보낸 PTT 음성 힌트 한 개 (FR-039~041).
 *
 * 문항별로 쌓여 **사용 이력**이 된다 — 어느 문항에서 힌트를 몇 번 줬는지가 그대로 남는다.
 */
@Entity
@Table(name = "voice_hint")
class VoiceHint(
    @Column(name = "room_id", nullable = false, updatable = false)
    val roomId: Long,

    @Column(name = "session_question_id", nullable = false, updatable = false)
    val sessionQuestionId: Long,

    /**
     * S3 오브젝트 **키**를 담는다. 컬럼 이름은 audio_url 이지만 완성된 URL 을 넣지 않는다 —
     * 버킷이 비공개라 재생 주소는 프리사인 URL 이고, 그건 만료되므로 저장하면 곧 죽은 링크가 된다.
     */
    @Column(name = "audio_url", nullable = false, updatable = false, length = AUDIO_KEY_MAX)
    val audioKey: String,

    @Column(name = "duration_ms", updatable = false)
    val durationMs: Int? = null,

    @Column(name = "published_at", nullable = false, updatable = false)
    val publishedAt: LocalDateTime = LocalDateTime.now(),
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    companion object {
        const val AUDIO_KEY_MAX = 500
    }
}
