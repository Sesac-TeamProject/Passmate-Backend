package kr.passmate.settlement.service

import kr.passmate.settlement.domain.HostEarning
import kr.passmate.settlement.dto.HostEarningRow
import kr.passmate.settlement.dto.HostEarningsResponse
import kr.passmate.settlement.repository.HostEarningRepository
import kr.passmate.room.service.RoomQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 호스트 수익 조회 (FR-056).
 */
@Service
@Transactional(readOnly = true)
class HostEarningQueryService(
    private val hostEarningRepository: HostEarningRepository,
    private val roomQueryService: RoomQueryService,
    private val settlementAccountService: SettlementAccountService,
    private val settlementSchedule: SettlementSchedule,
) {

    fun myEarnings(hostUserId: Long, now: LocalDateTime = LocalDateTime.now()): HostEarningsResponse {
        val earnings = hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostUserId)
        // 방 제목은 한 번에 읽어 붙인다 — 건마다 조회하면 내역 길이만큼 쿼리가 늘어난다
        val rooms = roomQueryService.getRooms(earnings.map { it.roomId })

        return HostEarningsResponse(
            thisMonthNet = earnings.filter { it.isIn(YearMonth.from(now)) }.sumOf { it.net },
            // 이월된 것도 아직 못 받은 돈이라 지급 예정액에 넣는다
            pendingNet = earnings.filter { it.payable }.sumOf { it.net },
            nextPayoutDate = settlementSchedule.nextPayoutDate(now.toLocalDate()),
            accountRegistered = settlementAccountService.hasAccount(hostUserId),
            earnings = earnings.map { HostEarningRow.of(it, rooms[it.roomId]?.title ?: "") },
        )
    }

    /** 내보내기가 같은 목록을 쓰도록 조회를 한 곳에 둔다. */
    fun rowsOf(hostUserId: Long): List<HostEarningRow> {
        val earnings = hostEarningRepository.findAllByHostUserIdOrderByEarnedAtDesc(hostUserId)
        val rooms = roomQueryService.getRooms(earnings.map { it.roomId })
        return earnings.map { HostEarningRow.of(it, rooms[it.roomId]?.title ?: "") }
    }

    private fun HostEarning.isIn(month: YearMonth) = YearMonth.from(earnedAt) == month
}

/**
 * 정산 지급일 (FR-057, 매월 5일).
 *
 * 날짜 계산만 하는 조각으로 떼어 둔다 — 조회와 배치가 같은 규칙을 봐야 한다.
 */
@Service
class SettlementSchedule {

    fun nextPayoutDate(today: LocalDate = LocalDate.now()): LocalDate =
        if (today.dayOfMonth < PAYOUT_DAY) today.withDayOfMonth(PAYOUT_DAY)
        else today.plusMonths(1).withDayOfMonth(PAYOUT_DAY)

    private companion object {
        /** 매월 5일 지급 */
        const val PAYOUT_DAY = 5
    }
}
