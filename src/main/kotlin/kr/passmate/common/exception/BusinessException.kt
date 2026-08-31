package kr.passmate.common.exception

/**
 * 업무 규칙 위반. Client 구현체는 외부 시스템 실패를 이 예외로 번역해서 던진다.
 * cause 는 로그에만 남고 응답 본문에는 나가지 않는다.
 */
class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
