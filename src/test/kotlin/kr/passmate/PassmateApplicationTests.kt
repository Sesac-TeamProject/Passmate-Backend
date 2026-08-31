package kr.passmate

import kr.passmate.support.IntegrationTestSupport
import org.junit.jupiter.api.Test

/**
 * 스프링 컨텍스트가 뜨고 Flyway V1(34 테이블)이 적용되며
 * ddl-auto: validate 가 엔티티-스키마 정합성을 통과하는지 확인한다.
 */
class PassmateApplicationTests : IntegrationTestSupport() {

    @Test
    fun `컨텍스트가 로드된다`() {
    }
}
