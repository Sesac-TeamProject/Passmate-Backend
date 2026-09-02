package kr.passmate.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 웹·앱 팀이 보는 API 문서. 운영 프로파일에서는 비활성(application-prod.yml). */
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {
        val bearer = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("PassMate API")
                    .description("AI 기반 실시간 문제풀이 플랫폼 백엔드. URL prefix 없음, 인증은 Bearer 액세스 토큰.")
                    .version("v2"),
            )
            .components(
                Components().addSecuritySchemes(
                    bearer,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
            .addSecurityItem(SecurityRequirement().addList(bearer))
    }
}
