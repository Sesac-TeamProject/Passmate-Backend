package kr.passmate.ai.repository

import kr.passmate.ai.domain.AiGenerationLog
import kr.passmate.ai.domain.AiGenerationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface AiGenerationLogRepository : JpaRepository<AiGenerationLog, Long> {

    /**
     * 무료 한도 집계. **kind 를 가리지 않는다** — 재생성도 AI 를 한 번 더 부르는 것이라
     * 생성과 같은 한도에 든다. 나중에 붙을 FILE 생성도 자동으로 포함된다.
     *
     * 실패는 횟수를 깎지 않으므로 SUCCESS 만 센다.
     */
    fun countByUserIdAndStatus(userId: Long, status: AiGenerationStatus): Long
}
