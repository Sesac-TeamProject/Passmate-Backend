package kr.passmate.voicehint.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 힌트 클립 한 개.
 *
 * [audioUrl] 은 **요청할 때마다 새로 서명된 임시 주소**다. 저장하거나 캐시하면 곧 만료된다.
 */
@Schema(description = "음성 힌트")
data class VoiceHintResponse(
    val hintId: Long,
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    @field:Schema(description = "잠깐만 열리는 재생 주소. 만료되므로 저장하지 않는다")
    val audioUrl: String,
    val durationMs: Int?,
    val publishedAt: LocalDateTime,
)

@Schema(description = "음성 힌트 목록")
data class VoiceHintListResponse(
    val roomId: Long,
    val totalCount: Int,
    val hints: List<VoiceHintResponse>,
)
