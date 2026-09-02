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

/**
 * `@CurrentUser` 파라미터를 채운다. 파라미터 타입이 곧 권한 선언이다.
 *
 * - `UserPrincipal` → 회원만. 게스트가 부르면 403
 * - `AuthPrincipal` → 회원·게스트 모두
 * - `GuestPrincipal` → 게스트만
 */
@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            AuthPrincipal::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthPrincipal? {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal
        val required = parameter.getParameterAnnotation(CurrentUser::class.java)?.required ?: true

        if (principal == null) {
            if (!required) return null
            // 필터가 만료·위조를 구분해 뒀으면 그 코드를 그대로 내보낸다 — 만료는 TOKEN_EXPIRED 여야
            // 클라이언트가 refresh 를 시도한다
            val errorCode = webRequest.getAttribute(
                JwtAuthenticationEntryPoint.ATTRIBUTE_ERROR_CODE,
                RequestAttributes.SCOPE_REQUEST,
            ) as? ErrorCode ?: ErrorCode.UNAUTHORIZED
            throw BusinessException(errorCode)
        }

        if (!parameter.parameterType.isInstance(principal)) {
            // 회원 전용 API 를 게스트 토큰으로 부른 경우가 대부분이다
            throw BusinessException(
                if (principal is GuestPrincipal) ErrorCode.GUEST_NOT_ALLOWED else ErrorCode.ACCESS_DENIED,
            )
        }
        return principal
    }
}
