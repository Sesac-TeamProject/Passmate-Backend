package kr.passmate.session.service

import kr.passmate.session.repository.SessionQuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 서버 권위 타이머.
 *
 * 제한시간이 지난 문항을 서버가 마감한다. 클라이언트가 "시간 다 됐어요"라고 알려주는 방식이면
 * 시계를 늦춰 시간을 벌 수 있고, 아무도 안 붙어 있으면 문항이 영영 안 닫힌다.
 *
 * TaskScheduler 로 endsAt 에 맞춰 예약하는 방법도 있지만 예약이 메모리에만 남아
 * 재기동하면 사라진다. 1초 폴링은 재기동해도 다음 틱에 알아서 따라잡고, 멱등이라
 * 호스트의 "바로 마감"과 겹쳐도 두 번 닫히지 않는다.
 */
@Component
class QuestionTimeoutScheduler(
    private val sessionQuestionRepository: SessionQuestionRepository,
    private val sessionService: SessionService,
    private val policy: kr.passmate.common.config.PolicyProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * **트랜잭션을 열지 않는다.** 여기서 `@Transactional(readOnly = true)` 로 감싸면
     * [SessionService.endByTimeout](REQUIRED)이 그 읽기 전용 트랜잭션에 합류해 `endedAt` 변경이 flush 되지 않고,
     * 다음 틱에 같은 문항을 또 찾아 QUESTION_ENDED 를 매초 다시 발행한다(웹 QA_BACKLOG B-12).
     * 열지 않으면 마감 한 건이 각자 쓰기 트랜잭션에서 끝나 커밋되고, 한 건이 실패해도 나머지가 함께 죽지 않는다.
     */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    fun closeExpiredQuestions() {
        val expired = sessionQuestionRepository.findAllByEndedAtIsNullAndEndsAtLessThan(LocalDateTime.now())
        expired.forEach { sq ->
            runCatching { sessionService.endByTimeout(sq.id) }
                .onFailure { log.warn("문항 자동 마감 실패 — sessionQuestionId={}", sq.id, it) }
        }

        // 자동 넘김(W-02b): 마감된 지 결과 표시 시간이 지난 문항의 다음 문항을 연다.
        // 곧바로 열지 않는 이유 — QUESTION_ENDED 의 정답·분포(W-06)를 볼 시간이 없어진다
        val due = sessionQuestionRepository
            .findAutoAdvanceDue(LocalDateTime.now().minusSeconds(policy.autoAdvanceDelaySeconds))
        due.forEach { sq ->
            runCatching { sessionService.advanceByAutoAdvance(sq.id) }
                .onFailure { log.warn("자동 넘김 실패 — sessionQuestionId={}", sq.id, it) }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
