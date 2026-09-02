package kr.passmate.room.domain

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RoomTest {

    @Test
    fun `시작 전에 닫으면 취소, 진행 중에 닫으면 종료다`() {
        assertThat(room().close()).isEqualTo(RoomStatus.CANCELED)
    }

    @Test
    fun `이미 닫힌 방은 다시 닫을 수 없다`() {
        val room = room().apply { close() }

        assertThatThrownBy { room.close() }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.CONFLICT)
    }

    @Test
    fun `호스트가 아니면 403 으로 막는다`() {
        assertThatThrownBy { room().verifyHost(999L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.NOT_ROOM_HOST)
    }

    @Test
    fun `대기 중이 아니면 수정할 수 없다`() {
        val room = room().apply { close() }

        assertThatThrownBy {
            room.update("새 제목", null, null, null, null, false, null)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.CONFLICT)
    }

    @Test
    fun `최대 인원이 없으면 정원이 차지 않는다`() {
        val room = room(maxParticipants = null)
        repeat(100) { room.increaseParticipantCount() }

        assertThat(room.isFull()).isFalse()
    }

    @Test
    fun `정원이 차면 입장을 막는다`() {
        val room = room(maxParticipants = 2)
        room.increaseParticipantCount()
        room.increaseParticipantCount()

        assertThatThrownBy { room.verifyJoinable() }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.ROOM_FULL)
    }

    @Test
    fun `대기 중이 아닌 방에는 입장할 수 없다`() {
        val room = room().apply { close() }

        assertThatThrownBy { room.verifyJoinable() }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.ROOM_NOT_JOINABLE)
    }

    @Test
    fun `인원 수는 0 아래로 내려가지 않는다`() {
        val room = room()
        room.decreaseParticipantCount()

        assertThat(room.participantCount).isZero()
    }

    private fun room(maxParticipants: Int? = null) = Room(
        hostUserId = 1L,
        pin = "123456",
        type = RoomType.FREE,
        title = "CS 면접 대비",
        maxParticipants = maxParticipants,
    )
}
