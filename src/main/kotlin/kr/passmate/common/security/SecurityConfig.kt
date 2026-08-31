package kr.passmate.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 권한 게이트는 전부 서버에서 판정한다. 클라이언트 화면 제어를 신뢰하지 않는다.
 * TODO(auth): JwtAuthenticationFilter 를 추가하고 anyRequest 를 authenticated 로 전환한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
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
            .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(*PUBLIC_PATHS).permitAll()
                it.requestMatchers("/admin/**").hasRole("ADMIN")
                it.anyRequest().permitAll()
            }
            .build()

    private fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    allowedOriginPatterns = listOf("http://localhost:*", "https://*.passmate.app")
                    allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("*")
                    allowCredentials = true
                    maxAge = 3600
                },
            )
        }

    companion object {
        private val PUBLIC_PATHS = arrayOf(
            "/actuator/health/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/ws/**",
            "/auth/**",
            "/webhooks/**",
        )
    }
}
