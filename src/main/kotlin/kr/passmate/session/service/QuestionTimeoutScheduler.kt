package kr.passmate.session.service

import kr.passmate.session.repository.SessionQuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    @Transactional(readOnly = true)
    fun closeExpiredQuestions() {
        val expired = sessionQuestionRepository.findAllByEndedAtIsNullAndEndsAtLessThan(LocalDateTime.now())
        expired.forEach { sq ->
            runCatching { sessionService.endByTimeout(sq.id) }
                .onFailure { log.warn("문항 자동 마감 실패 — sessionQuestionId={}", sq.id, it) }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
