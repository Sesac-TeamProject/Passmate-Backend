package kr.passmate.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 모든 예외의 단일 출구. 내부 원인은 로그에만 남기고 응답에는 {code, message} 만 내보낸다.
 * 컨트롤러·서비스에서 try/catch 로 응답을 만들지 않는다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("[{}] {} {} — {}", e.errorCode.name, request.method, request.requestURI, e.message, e.cause)
        return ResponseEntity.status(e.errorCode.status)
            .body(ErrorResponse.of(e.errorCode, e.message, e.data))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { it.toMessage() }
            .ifBlank { ErrorCode.INVALID_INPUT.message }
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status)
            .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message))
    }

    /**
     * 읽을 수 없는 요청 본문 — 깨진 JSON, 필수 필드 누락, 타입 불일치.
     *
     * 이걸 안 잡으면 마지막 Exception 핸들러로 떨어져 **500** 이 나간다.
     * 잘못 보낸 쪽은 클라이언트인데 서버 장애처럼 보이고, 재시도해도 될 것처럼 읽힌다.
     * 파서 메시지에는 내부 클래스 이름이 섞이므로 응답에는 싣지 않고 로그에만 남긴다.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("요청 본문을 읽지 못함 — {} {}: {}", request.method, request.requestURI, e.mostSpecificCause.message)
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status)
            .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 본문을 읽을 수 없습니다. 필수 항목과 형식을 확인해 주세요."))
    }

    /** 필수 쿼리 파라미터 누락. 같은 이유로 400 이다. */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.INVALID_INPUT.status)
            .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "${e.parameterName} 은(는) 필수 항목입니다."))

    /** 경로·쿼리 값의 타입이 안 맞는 경우(예: id 자리에 문자열). */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.INVALID_INPUT.status)
            .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "${e.name} 값의 형식이 올바르지 않습니다."))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.ACCESS_DENIED.status)
            .body(ErrorResponse.of(ErrorCode.ACCESS_DENIED))

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.NOT_FOUND.status)
            .body(ErrorResponse.of(ErrorCode.NOT_FOUND))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("처리하지 못한 예외 — {} {}", request.method, request.requestURI, e)
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status)
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR))
    }

    private fun FieldError.toMessage() = "$field: ${defaultMessage ?: "올바르지 않은 값"}"
}
