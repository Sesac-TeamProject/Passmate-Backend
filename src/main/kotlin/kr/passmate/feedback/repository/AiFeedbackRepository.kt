package kr.passmate.feedback.repository

import kr.passmate.feedback.domain.AiFeedback
import kr.passmate.feedback.domain.AiFeedbackStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface AiFeedbackRepository : JpaRepository<AiFeedback, Long> {

    /** 답안당 한 줄이라 유일하다(uk_ai_feedback_answer). */
    fun findByAnswerId(answerId: Long): AiFeedback?

    /** 여러 답안의 분석을 한 번에. 결과 화면·첨삭 목록에서 N+1 을 피하려고 쓴다. */
    fun findAllByAnswerIdIn(answerIds: Collection<Long>): List<AiFeedback>

    /**
     * 이번 달에 **무료로** 쓴 건수. `charged_coins = 0` 이 곧 무료 사용분이다.
     *
     * 실패한 건은 세지 않는다 — 문제 생성과 같은 원칙으로, 실패가 무료 횟수를 깎으면
     * 우리 쪽 장애를 사용자가 부담하게 된다. 환급된 건도 FAILED 라 여기서 빠진다.
     */
    @Query(
        """
        select count(f) from AiFeedback f
        where f.userId = :userId
          and f.chargedCoins = 0
          and f.status <> :excluded
          and f.createdAt >= :from
          and f.createdAt < :to
        """,
    )
    fun countFreeUsage(
        @Param("userId") userId: Long,
        @Param("excluded") excluded: AiFeedbackStatus,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): Long
}
