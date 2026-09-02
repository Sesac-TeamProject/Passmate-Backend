package kr.passmate.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.exception.ErrorResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/** 인증은 됐지만 권한이 모자란 경우 — 403 을 다른 오류와 같은 {code, message} 형식으로 내보낸다. */
@Component
class JwtAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = ErrorCode.ACCESS_DENIED.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, ErrorResponse.of(ErrorCode.ACCESS_DENIED))
    }
}
