package kr.passmate.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.passmate.common.exception.BusinessException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer <access token> 을 읽어 SecurityContext 를 채운다.
 *
 * 토큰이 없거나 잘못됐다고 여기서 응답을 만들지 않는다 — 인증 정보를 비운 채 통과시키고,
 * 보호된 경로면 JwtAuthenticationEntryPoint 가 **401** 로 마감한다. 만료 사유는 요청 속성에 실어 보낸다.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        resolveToken(request)?.let { token ->
            try {
                val principal = jwtTokenProvider.parseAccessToken(token)
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
            } catch (e: BusinessException) {
                SecurityContextHolder.clearContext()
                request.setAttribute(JwtAuthenticationEntryPoint.ATTRIBUTE_ERROR_CODE, e.errorCode)
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader(HEADER)
            ?.takeIf { it.startsWith(PREFIX, ignoreCase = true) }
            ?.substring(PREFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    companion object {
        private const val HEADER = "Authorization"
        private const val PREFIX = "Bearer "
    }
}
