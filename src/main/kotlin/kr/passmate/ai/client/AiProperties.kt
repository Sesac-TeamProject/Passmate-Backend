package kr.passmate.ai.client

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * AI 설정. 제공자는 OpenAI(2026-08-31 결정).
 *
 * 모델 이름을 코드에 박지 않는다 — 가격·성능이 바뀌면 env 만 갈아끼운다.
 * 용도가 다르면 모델도 다르다: 문제 생성은 형식 정확도가, 서술형 분석은 단가가 중요하다.
 */
@ConfigurationProperties(prefix = "passmate.ai")
data class AiProperties(
    val provider: String,
    val baseUrl: String,
    /** 비어 있으면 AI 기능을 부르는 시점에 502 로 막는다 — 조용히 실패하지 않는다. */
    val apiKey: String,
    /** 문제 세트 생성용. Structured Outputs(json_schema strict) 를 지원해야 한다. */
    val generationModel: String,
    /** 서술형 답안 분석용. 문항 × 참가자 수만큼 호출되므로 가장 싼 축을 쓴다. */
    val analysisModel: String,
    /** low / medium / high. 문제 생성은 30초 SLA 라 기본을 낮게 둔다. */
    val reasoningEffort: String,
    val timeoutSeconds: Long,
    /** 서술형 분석 동시 실행 상한. 세션 실시간 경로를 막지 않기 위한 세마포어 크기. */
    val maxConcurrentAnalysis: Int,
) {
    val timeout: Duration get() = Duration.ofSeconds(timeoutSeconds)
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
