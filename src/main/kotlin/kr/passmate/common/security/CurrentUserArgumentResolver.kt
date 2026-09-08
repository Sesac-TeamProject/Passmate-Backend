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
            // 주체가 선택인 API(공개 방 목록 등)는 게스트 토큰을 달고 와도 비로그인처럼 다룬다 —
            // 방에 들어갔다 나온 브라우저가 게스트 토큰을 들고 있는 채로 공개 목록을 열면 403 이 났다
            // (2026-09-09 시나리오 테스트 S-07). 회원만 받는 필수 파라미터는 지금처럼 막는다
            if (!required) return null
            // 회원 전용 API 를 게스트 토큰으로 부른 경우가 대부분이다
            throw BusinessException(
                if (principal is GuestPrincipal) ErrorCode.GUEST_NOT_ALLOWED else ErrorCode.ACCESS_DENIED,
            )
        }
        return principal
    }
}
