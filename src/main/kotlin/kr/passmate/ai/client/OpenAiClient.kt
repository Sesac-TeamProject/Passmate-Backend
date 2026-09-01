package kr.passmate.ai.client

/**
 * OpenAI 호출. 구현체가 HTTP·스키마를 감추고, Service 는 조건만 넘긴다.
 * 테스트는 Fake 구현으로 갈아끼운다 — 자동화 테스트가 유료 API 를 부르는 일은 없어야 한다.
 */
interface OpenAiClient {

    /** 조건에 맞는 문항을 생성한다. 형식·통신 오류는 [AiGenerationException] 으로 던진다. */
    fun generateQuestions(request: AiGenerationRequest): AiGenerationResult
}
