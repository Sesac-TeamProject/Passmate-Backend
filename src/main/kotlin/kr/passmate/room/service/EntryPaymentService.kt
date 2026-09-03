package kr.passmate.room.service

import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransactionType
import kr.passmate.coin.service.CoinService
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.room.domain.EntryPayment
import kr.passmate.room.domain.EntryPaymentStatus
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.EntryPaymentResponse
import kr.passmate.room.repository.EntryPaymentRepository
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * 유료 방 참가비 (FR-050 · FR-051). **회원 전용** — 게스트는 컨트롤러에서 이미 걸린다.
 *
 * room 이 coin 을 부르는 방향이다. 반대로 두면 입장 게이트(coin 이 room 의 입장을 막는 구조)에서
 * 순환이 생긴다.
 */
@Service
class EntryPaymentService(
    private val roomRepository: RoomRepository,
    private val entryPaymentRepository: EntryPaymentRepository,
    private val coinService: CoinService,
) {

    /**
     * 참가비를 코인에서 차감하고 영수증을 발급한다. 입장 자격은 이 결제 행이 곧 근거다.
     *
     * 방에 비관적 락을 건다 — 정원 확인과 결제 사이에 다른 결제가 끼어들면
     * 정원을 넘긴 사람에게서 참가비를 걷고 입장은 막는 상태가 된다.
     */
    @Transactional
    fun pay(roomId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): EntryPaymentResponse {
        val room = roomRepository.findByIdForUpdate(roomId)
            ?: throw BusinessException(ErrorCode.ROOM_NOT_FOUND)

        val fee = verifyPaidRoom(room)
        room.verifyJoinable()
        if (activePaymentOf(roomId, userId) != null) {
            throw BusinessException(ErrorCode.ALREADY_PAID)
        }

        // 원장 memo 가 payment.id 를 참조하므로 결제 행을 먼저 확정한다
        val payment = entryPaymentRepository.saveAndFlush(
            EntryPayment.of(
                paymentNo = issuePaymentNo(now.toLocalDate()),
                roomId = roomId,
                userId = userId,
                amount = fee,
                paidAt = now,
            ),
        )

        // 방 제목·영수증 번호를 지금 박아 둔다 — 코인 내역이 room 을 되짚지 않아도 되게
        val transaction = coinService.deduct(
            userId = userId,
            amount = fee,
            type = CoinTransactionType.ENTRY,
            refType = CoinRefType.ENTRY_PAYMENT,
            refId = payment.id,
            memo = "${room.title} · ${payment.paymentNo}",
        )
        return EntryPaymentResponse.of(payment, transaction.balanceAfter)
    }

    /**
     * 입장 게이트 (FR-051). 유료 방은 살아 있는 결제가 있어야 들어갈 수 있다.
     * 통과하면 그 결제에 참가자를 연결한다(ERD entry_payment.participant_id).
     */
    @Transactional
    fun consumeForJoin(room: Room, userId: Long?, participantId: Long) {
        if (room.type == RoomType.FREE) return
        // 유료 방은 회원 전용이라 여기 오는 게스트는 없다(ParticipantService 가 먼저 막는다).
        // 그래도 null 이면 결제가 있을 수 없으므로 게이트에 걸리는 게 맞다
        val payment = userId?.let { activePaymentOf(room.id, it) }
            ?: throw BusinessException(ErrorCode.ENTRY_FEE_REQUIRED)
        payment.linkParticipant(participantId)
    }

    /** 이 방에 살아 있는 내 결제. 없으면 null */
    @Transactional(readOnly = true)
    fun activePaymentOf(roomId: Long, userId: Long): EntryPayment? =
        entryPaymentRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, EntryPaymentStatus.PAID)

    /** 참가비를 받는 방인지 확인하고 그 금액을 준다. */
    private fun verifyPaidRoom(room: Room): Int {
        if (room.type == RoomType.FREE) {
            throw BusinessException(ErrorCode.NOT_PAID_ROOM)
        }
        return room.fee ?: throw BusinessException(ErrorCode.NOT_PAID_ROOM)
    }

    /**
     * 영수증 번호 PM-YYYY-MMDD-NNNN.
     *
     * 뒤 네 자리는 그날의 일련번호가 아니라 **난수**다 — 일련번호로 하면 동시에 결제하는
     * 두 사람이 같은 번호를 잡고, UK 에 걸려 한쪽이 실패한다. 난수 + 중복 확인이면
     * 재시도가 다른 값으로 이어진다. 사용자에게는 어차피 조회용 식별자다.
     */
    private fun issuePaymentNo(date: LocalDate): String {
        val prefix = "PM-${date.format(DATE_FORMAT)}-"
        repeat(PAYMENT_NO_ATTEMPTS) {
            val candidate = prefix + "%04d".format(Random.nextInt(SUFFIX_BOUND))
            if (!entryPaymentRepository.existsByPaymentNo(candidate)) return candidate
        }
        throw BusinessException(ErrorCode.INTERNAL_ERROR, "결제 번호 발급에 실패했습니다. 다시 시도해 주세요.")
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MMdd")
        private const val PAYMENT_NO_ATTEMPTS = 10
        private const val SUFFIX_BOUND = 10_000
    }
}
