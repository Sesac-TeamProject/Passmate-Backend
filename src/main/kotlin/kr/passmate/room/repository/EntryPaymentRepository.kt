package kr.passmate.room.repository

import jakarta.persistence.LockModeType
import kr.passmate.room.domain.EntryPayment
import kr.passmate.room.domain.EntryPaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EntryPaymentRepository : JpaRepository<EntryPayment, Long> {

    fun existsByPaymentNo(paymentNo: String): Boolean

    /** 이 방에 살아 있는 내 결제. 중복 결제 차단과 입장 게이트가 함께 쓴다. */
    fun findByRoomIdAndUserIdAndStatus(
        roomId: Long,
        userId: Long,
        status: EntryPaymentStatus,
    ): EntryPayment?

    /**
     * 환급 처리용 비관적 락. 취소 요청이 두 번 겹쳐도 한쪽만 상태를 바꾸게 한다 —
     * 코인 환급 자체는 CoinService 가 멱등이지만, 상태 전이까지 겹치면
     * 환급 사유·처리자가 나중 요청 값으로 덮인다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EntryPayment p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): EntryPayment?

    /** 이 방에서 실제로 걷힌 참가비 총액. 호스트 수익 적립의 gross 다. */
    @Query(
        """
        select coalesce(sum(p.amount), 0)
        from EntryPayment p
        where p.roomId = :roomId and p.status = :status
        """,
    )
    fun sumAmountByRoomIdAndStatus(
        @Param("roomId") roomId: Long,
        @Param("status") status: EntryPaymentStatus,
    ): Int

    fun countByRoomIdAndStatus(roomId: Long, status: EntryPaymentStatus): Int
}
