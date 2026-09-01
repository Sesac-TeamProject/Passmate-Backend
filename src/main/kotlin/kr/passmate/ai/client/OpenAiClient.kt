package kr.passmate.ai.client

/**
 * OpenAI 호출. 구현체가 HTTP·스키마를 감추고, Service 는 조건만 넘긴다.
 * 테스트는 Fake 구현으로 갈아끼운다 — 자동화 테스트가 유료 API 를 부르는 일은 없어야 한다.
 *
 * 실패는 전부 [AiCallException] 으로 나온다. 호출자가 retryable 을 보고 재시도를 정한다.
 */
interface OpenAiClient {

    /** 조건에 맞는 문항을 생성한다. */
    fun generateQuestions(request: AiGenerationRequest): AiGenerationResult

    /**
     * 서술형 답안을 모범답안과 견줘 분석한다.
     * 문항 × 참가자 수만큼 불릴 수 있어 **가장 싼 모델**(`analysis-model`)을 쓴다.
     */
    fun analyzeEssay(request: EssayAnalysisRequest): EssayAnalysisResult
}
