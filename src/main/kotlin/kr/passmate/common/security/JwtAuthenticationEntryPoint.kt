package kr.passmate.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.exception.ErrorResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * 인증 실패·토큰 만료는 반드시 401 로 응답한다.
 * 403 을 내려보내면 클라이언트의 refresh 재시도 로직이 발화하지 않는다.
 */
@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val errorCode = request.getAttribute(ATTRIBUTE_ERROR_CODE) as? ErrorCode ?: ErrorCode.UNAUTHORIZED
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, ErrorResponse.of(errorCode))
    }

    companion object {
        /** JWT 필터가 만료·서명오류를 구분해 담아두면 그대로 내보낸다. */
        const val ATTRIBUTE_ERROR_CODE = "passmate.errorCode"
    }
}
