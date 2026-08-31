package kr.passmate.room.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.room.repository.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PinServiceTest {

    private val roomRepository = mockk<RoomRepository>()
    private val pinService = PinService(roomRepository)

    @Test
    fun `6자리 숫자 PIN 을 발급한다`() {
        every { roomRepository.existsByPinAndStatusIn(any(), any()) } returns false

        val pin = pinService.issue()

        assertThat(pin).hasSize(6).containsOnlyDigits()
    }

    @Test
    fun `활성 방과 겹치면 다시 뽑는다`() {
        // 앞의 두 번은 충돌, 세 번째에 성공
        every { roomRepository.existsByPinAndStatusIn(any(), any()) } returnsMany listOf(true, true, false)

        val pin = pinService.issue()

        assertThat(pin).hasSize(6)
        verify(exactly = 3) { roomRepository.existsByPinAndStatusIn(any(), any()) }
    }

    @Test
    fun `계속 충돌하면 무한히 돌지 않고 실패한다`() {
        every { roomRepository.existsByPinAndStatusIn(any(), any()) } returns true

        assertThatThrownBy { pinService.issue() }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.PIN_GENERATION_FAILED)
    }

    @Test
    fun `종료된 방은 조회 대상이 아니다`() {
        every { roomRepository.existsByPinAndStatusIn(any(), any()) } returns false

        pinService.issue()

        verify { roomRepository.existsByPinAndStatusIn(any(), PinService.ACTIVE_STATUSES) }
    }
}
