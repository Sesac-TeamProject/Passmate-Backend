package kr.passmate.common.exception

/** 모든 오류 응답의 본문 형식. */
data class ErrorResponse(
    val code: String,
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message) =
            ErrorResponse(errorCode.name, message)
    }
}
