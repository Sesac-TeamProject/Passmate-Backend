package kr.passmate.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 권한 게이트는 전부 서버에서 판정한다. 클라이언트 화면 제어를 신뢰하지 않는다.
 * 인증 실패는 401(JwtAuthenticationEntryPoint), 권한 부족은 403(JwtAccessDeniedHandler).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val accessDeniedHandler: JwtAccessDeniedHandler,
    private val corsProperties: CorsProperties,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(*PUBLIC_PATHS).permitAll()
                // 게스트 입장 흐름 — 가입 없이 30초 안에 들어가는 것이 핵심 차별점이라 인증을 걸지 않는다
                // 홈 인기 방·탐색은 게스트도 본다(FR-054). PIN 은 응답에 넣지 않는다
                it.requestMatchers(HttpMethod.GET, "/rooms/public").permitAll()
                it.requestMatchers(HttpMethod.GET, "/rooms/pin/*").permitAll()
                // 광고는 결과 화면·대기실 배너에 뜬다 — 게스트도 봐야 한다(FR-073)
                it.requestMatchers(HttpMethod.GET, "/ads").permitAll()
                it.requestMatchers(HttpMethod.POST, "/ads/*/events").permitAll()
                // 포트원이 부르는 서버 간 호출이라 우리 토큰이 있을 수 없다.
                // 방벽은 인증이 아니라 웹훅 서명이다(PortOneWebhookVerifier)
                it.requestMatchers(HttpMethod.POST, "/webhooks/portone").permitAll()
                // 선생님 공개 프로필은 탐색에서 로그인 없이 열린다(FR-066)
                it.requestMatchers(HttpMethod.GET, "/users/*/profile").permitAll()
                it.requestMatchers(HttpMethod.GET, "/rooms/*/participants/nickname-check").permitAll()
                it.requestMatchers(HttpMethod.POST, "/rooms/*/participants").permitAll()
                it.requestMatchers("/admin/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    private fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    // 운영 도메인은 WEB_BASE_URL 에서 온다(CorsProperties). 코드에 박지 않는다 —
                    // 도메인이 바뀌어도 설정값 교체 + 재배포로 끝나야 한다
                    allowedOriginPatterns = corsProperties.originPatterns
                    allowedMethods = corsProperties.allowedMethods
                    allowedHeaders = listOf("*")
                    allowCredentials = true
                    maxAge = corsProperties.maxAgeSeconds
                },
            )
        }

    companion object {
        /** 비로그인으로 열어야 하는 경로. 게스트 입장 경로는 room 기능에서 추가한다. */
        private val PUBLIC_PATHS = arrayOf(
            "/actuator/health/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            // WebSocket 핸드셰이크는 HTTP 필터를 통과시키고, 인증은 STOMP CONNECT 프레임에서 한다.
            // 브라우저 WebSocket 은 커스텀 헤더를 못 붙여 Authorization 을 핸드셰이크에 실을 수 없다
            "/ws/**",
            "/auth/**",
            "/webhooks/**",
        )
    }
}
