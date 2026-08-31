package kr.passmate.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
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
            .body(ErrorResponse.of(e.errorCode, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { it.toMessage() }
            .ifBlank { ErrorCode.INVALID_INPUT.message }
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status)
            .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message))
    }

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
