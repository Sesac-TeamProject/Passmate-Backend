package kr.passmate.settlement.service

import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.event.SessionEndedEvent
import kr.passmate.room.service.EntryPaymentService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.settlement.domain.HostEarning
import kr.passmate.settlement.repository.HostEarningRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 세션이 끝나면 걷힌 참가비를 호스트 수익으로 적립한다 (FR-055).
 *
 * **host_earning 을 만드는 유일한 자리**다. 이 경로가 없으면 참가비는 걷히는데
 * 정산 조회는 영원히 빈 목록이다.
 *
 * session 을 직접 부르지 않고 이벤트로 받는다 — settlement 를 session 이 알게 되면
 * 종료 경로가 정산까지 짊어진다.
 */
@Service
class HostEarningAccrualService(
    private val hostEarningRepository: HostEarningRepository,
    private val roomQueryService: RoomQueryService,
    private val entryPaymentService: EntryPaymentService,
    private val policyProperties: PolicyProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Transactional
    fun onSessionEnded(event: SessionEndedEvent) {
        accrue(event.roomId)
    }

    /**
     * 방당 한 줄(uk_host_earning_room)이라 이미 있으면 아무것도 하지 않는다 —
     * 종료 이벤트가 두 번 와도 수익이 두 배가 되지 않아야 한다.
     *
     * 배분율은 **적립 시점 값을 계산해 박아 둔다.** 나중에 비율이 바뀌어도
     * 이미 끝난 세션의 정산액은 그때 약속한 값이어야 한다(HostEarning.of).
     */
    @Transactional
    fun accrue(roomId: Long, now: LocalDateTime = LocalDateTime.now()) {
        if (hostEarningRepository.existsByRoomId(roomId)) {
            log.debug("이미 적립된 방이라 건너뛴다 roomId={}", roomId)
            return
        }

        val room = roomQueryService.getRoom(roomId)
        val settled = entryPaymentService.settledOf(roomId)
        // 무료 방이거나 아무도 결제하지 않은 방은 적립할 것이 없다.
        // 0원짜리 줄을 남기면 정산 화면이 빈 내역으로 가득 찬다
        if (settled.gross <= 0) return

        hostEarningRepository.save(
            HostEarning.of(
                roomId = roomId,
                hostUserId = room.hostUserId,
                participantCount = settled.payerCount,
                gross = settled.gross,
                hostRate = policyProperties.hostEarningRate,
                earnedAt = room.endedAt ?: now,
            ),
        )
    }
}
