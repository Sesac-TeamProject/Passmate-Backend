package kr.passmate.common.exception

/**
 * 업무 규칙 위반. Client 구현체는 외부 시스템 실패를 이 예외로 번역해서 던진다.
 * cause 는 로그에만 남고 응답 본문에는 나가지 않는다.
 *
 * [data] 는 클라이언트가 **다음 행동을 정하는 데 필요한 값**만 담는다 —
 * 코인이 얼마나 모자란지 같은 것. 내부 사정을 흘리는 통로가 아니다.
 */
class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    cause: Throwable? = null,
    val data: Map<String, Any>? = null,
) : RuntimeException(message, cause)
