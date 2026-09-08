package kr.passmate.session.service

import io.mockk.mockk
import io.mockk.verify
import kr.passmate.session.domain.SessionEventType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 브로드캐스트 시점 검증 — 커밋 전에 나가면 QUESTION_STARTED 직후 제출이
 * 커밋 전 스냅샷을 읽어 409 가 난다(시나리오 테스트, 2026-09-08).
 */
class SessionEventPublisherTest {

    private val template = mockk<SimpMessagingTemplate>(relaxed = true)
    private val publisher = SessionEventPublisher(template)

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `트랜잭션 안에서는 커밋 후에야 발행된다`() {
        TransactionSynchronizationManager.initSynchronization()

        publisher.toRoom(1L, SessionEventType.QUESTION_STARTED)
        verify(exactly = 0) { template.convertAndSend(any<String>(), any<Any>()) }

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        verify(exactly = 1) { template.convertAndSend("/topic/rooms/1", any<Any>()) }
    }

    @Test
    fun `트랜잭션 안에서 등록 순서가 발행 순서다`() {
        TransactionSynchronizationManager.initSynchronization()

        publisher.toRoom(1L, SessionEventType.QUESTION_ENDED)
        publisher.toRoom(1L, SessionEventType.RANKING_UPDATED)

        val order = mutableListOf<SessionEventType>()
        io.mockk.every { template.convertAndSend(any<String>(), any<Any>()) } answers {
            order += (secondArg<Any>() as kr.passmate.session.dto.SessionEvent<*>).type
        }
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        org.assertj.core.api.Assertions.assertThat(order)
            .containsExactly(SessionEventType.QUESTION_ENDED, SessionEventType.RANKING_UPDATED)
    }

    @Test
    fun `트랜잭션 밖에서는 즉시 발행된다`() {
        publisher.toHost(2L, SessionEventType.SUBMISSION_UPDATED)

        verify(exactly = 1) { template.convertAndSend("/topic/rooms/2/host", any<Any>()) }
    }
}
