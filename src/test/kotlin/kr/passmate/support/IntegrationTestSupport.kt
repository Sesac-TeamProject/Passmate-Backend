package kr.passmate.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

/**
 * 통합 테스트 공통 베이스. MySQL 8 컨테이너를 띄우고 Flyway 로 실제 스키마를 적용한다.
 * 외부 Client(OpenAI·PortOne·Google·FCM·S3)는 Fake 로 대체한다 — 테스트가 유료 API 를 부르지 않는다.
 *
 * 컨테이너는 **JVM 당 하나**를 직접 띄우고 끝까지 살려둔다.
 * `@Testcontainers` + `@Container` 를 쓰면 테스트 클래스가 끝날 때마다 컨테이너를 내려서,
 * 같은 static 인스턴스를 공유하는 다음 클래스가 "Connection refused" 로 죽는다.
 * 정리는 Testcontainers 의 Ryuk 이 JVM 종료 시 해 준다.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    companion object {
        @JvmStatic
        private val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("passmate")
            .withUsername("passmate")
            .withPassword("passmate")
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_0900_ai_ci",
                "--default-time-zone=+00:00",
            )
            .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
