package kr.passmate.common.exception

/**
 * 모든 오류 응답의 본문 형식.
 *
 * [data] 는 클라이언트가 다음 행동을 정하는 데 필요할 때만 붙는다(예: 부족 코인).
 * non_null 직렬화라 없으면 필드 자체가 빠져서 기존 응답 모양은 그대로다.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val data: Map<String, Any>? = null,
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message, data: Map<String, Any>? = null) =
            ErrorResponse(errorCode.name, message, data)
    }
}
