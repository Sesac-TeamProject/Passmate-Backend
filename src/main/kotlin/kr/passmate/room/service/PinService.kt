package kr.passmate.room.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import java.security.SecureRandom

/**
 * 방 입장 PIN 발급. 6자리 숫자이고 **활성 방 사이에서만** 유일하다 — 종료된 방의 PIN 은 다시 쓴다.
 *
 * Redis 후순위 결정에 따라 지금은 `생성 → 활성 방 조회 → 충돌이면 재생성` 으로 구현한다.
 * Redis 를 도입하면 이 클래스만 SETNX 방식으로 바꾸면 된다.
 *
 * 조회와 INSERT 사이에 다른 요청이 같은 PIN 을 쓸 여지는 남는다. 매우 드물고,
 * 그때는 방 생성이 실패하므로 클라이언트가 재시도하면 된다.
 */
@Service
class PinService(
    private val roomRepository: RoomRepository,
) {
    private val random = SecureRandom()

    fun issue(): String {
        repeat(MAX_ATTEMPTS) {
            val pin = randomPin()
            if (!roomRepository.existsByPinAndStatusIn(pin, ACTIVE_STATUSES)) {
                return pin
            }
        }
        throw BusinessException(ErrorCode.PIN_GENERATION_FAILED)
    }

    private fun randomPin(): String =
        random.nextInt(PIN_BOUND).toString().padStart(PIN_LENGTH, '0')

    companion object {
        private const val PIN_LENGTH = 6
        private const val PIN_BOUND = 1_000_000
        private const val MAX_ATTEMPTS = 10
        val ACTIVE_STATUSES = listOf(RoomStatus.WAITING, RoomStatus.RUNNING)
    }
}
