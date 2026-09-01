package kr.passmate.ai.repository

import kr.passmate.ai.domain.AiGenerationKind
import kr.passmate.ai.domain.AiGenerationLog
import kr.passmate.ai.domain.AiGenerationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface AiGenerationLogRepository : JpaRepository<AiGenerationLog, Long> {

    /** 무료 한도 집계. 실패는 횟수를 깎지 않으므로 SUCCESS 만 센다. */
    fun countByUserIdAndKindAndStatus(
        userId: Long,
        kind: AiGenerationKind,
        status: AiGenerationStatus,
    ): Long
}
