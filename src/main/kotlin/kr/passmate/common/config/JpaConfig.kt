package kr.passmate.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/** BaseTimeEntity 의 created_at / updated_at 자동 기록. */
@Configuration
@EnableJpaAuditing
class JpaConfig
