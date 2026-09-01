package kr.passmate.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * CORS 허용 출처. **브라우저(웹)만 해당된다** — 네이티브 앱은 CORS 대상이 아니다.
 *
 * 운영 값은 SSM 의 `WEB_BASE_URL`(웹 배포 주소)에서 온다. 도메인을 코드에 박지 않아야
 * 도메인이 바뀌어도 재배포만으로 끝나고, 코드 수정·재빌드가 필요 없다.
 */
@ConfigurationProperties(prefix = "passmate.cors")
data class CorsProperties(
    /** 허용 출처 패턴. `http://localhost:*` 처럼 와일드카드를 쓸 수 있다(= allowedOriginPatterns). */
    val allowedOrigins: List<String> = emptyList(),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS"),
    val maxAgeSeconds: Long = 3600,
) {
    /**
     * 빈 문자열을 걸러낸 허용 목록.
     * env 가 비어 있으면 `""` 하나짜리 리스트가 들어오는데, 그 상태로 두면
     * 아무 출처에도 맞지 않아 "설정은 했는데 전부 막히는" 상태가 조용히 만들어진다.
     */
    val originPatterns: List<String>
        get() = allowedOrigins.map(String::trim).filter(String::isNotEmpty)
}
