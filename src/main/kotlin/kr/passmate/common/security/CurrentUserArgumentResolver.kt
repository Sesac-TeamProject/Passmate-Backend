package kr.passmate.common.security

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            UserPrincipal::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UserPrincipal? {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
        val required = parameter.getParameterAnnotation(CurrentUser::class.java)?.required ?: true
        if (principal == null && required) {
            // 필터가 만료·위조를 구분해 뒀으면 그 코드를 그대로 내보낸다 — 만료는 TOKEN_EXPIRED 여야
            // 클라이언트가 refresh 를 시도한다
            val errorCode = webRequest.getAttribute(
                JwtAuthenticationEntryPoint.ATTRIBUTE_ERROR_CODE,
                RequestAttributes.SCOPE_REQUEST,
            ) as? ErrorCode ?: ErrorCode.UNAUTHORIZED
            throw BusinessException(errorCode)
        }
        return principal
    }
}
