package kr.passmate.ai.client

/**
 * AI 호출 실패. 문항 생성·서술형 분석 등 [OpenAiClient] 의 **모든 호출**이 이걸 던진다.
 *
 * Service 가 이걸 잡아 [retryable] 일 때만 **1회 재시도**하고, 그래도 실패하면 502 로 번역한다.
 * 인증 실패(401)·잘못된 요청(400)처럼 다시 걸어도 결과가 같은 실패는 재시도하지 않는다 —
 * 실패한 호출도 요청 자체는 나가므로 무의미한 재시도를 만들지 않는다.
 */
class AiCallException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
