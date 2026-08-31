package kr.passmate.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 통합 테스트 공통 베이스. MySQL 8 컨테이너를 띄우고 Flyway 로 실제 스키마를 적용한다.
 * 컨테이너는 클래스 간에 재사용되므로 테스트마다 재기동되지 않는다.
 * 외부 Client(Anthropic·PortOne·Google·FCM·S3)는 Fake 구현으로 대체한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class IntegrationTestSupport {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("passmate")
            .withUsername("passmate")
            .withPassword("passmate")
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_0900_ai_ci",
                "--default-time-zone=+00:00",
            )
            .withReuse(true)
    }
}
