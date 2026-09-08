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
        room.syncParticipantCount(100)

        assertThat(room.isFull()).isFalse()
    }

    @Test
    fun `정원이 차면 입장을 막는다`() {
        val room = room(maxParticipants = 2)
        room.syncParticipantCount(2)

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
    fun `인원 수는 실제 참가자 행 수로 맞춰지고 음수는 0 이 된다`() {
        // 증감이 아니라 재계산 — 한 번 어긋난 값이 남지 않는다(2026-09-08)
        val room = room()
        room.syncParticipantCount(3)
        assertThat(room.participantCount).isEqualTo(3)

        room.syncParticipantCount(-1)
        assertThat(room.participantCount).isZero()
    }

    // ---------- 문항별 시간 오버라이드 (W-02b, 웹 버그 리포트 B-18) ----------

    @Test
    fun `덮어쓴 시간이 없는 문항은 세트 기본값을 쓴다`() {
        val room = room().apply { overrideQuestionTimes(mapOf(1L to 20)) }

        assertThat(room.timeLimitSecOf(1L, 30)).isEqualTo(20)
        assertThat(room.timeLimitSecOf(2L, 30)).isEqualTo(30)
        assertThat(room.hasTimeOverride(1L)).isTrue()
        assertThat(room.hasTimeOverride(2L)).isFalse()
    }

    @Test
    fun `빈 맵으로 덮어쓰면 전부 기본값으로 돌아간다`() {
        val room = room().apply { overrideQuestionTimes(mapOf(1L to 20)) }

        room.overrideQuestionTimes(emptyMap())

        assertThat(room.questionTimeOverrides).isNull()
        assertThat(room.timeLimitSecOf(1L, 30)).isEqualTo(30)
    }

    @Test
    fun `자동 넘김은 기본 켬이고 끈 문항만 거짓이다`() {
        // 2026-09-08 시나리오 테스트 반전 — 시간이 끝나면 바로 다음 문제로 가는 것이 기본
        val room = room(questionSetId = 10L)
        assertThat(room.isAutoAdvance(1L)).isTrue()

        room.overrideQuestionTimes(mapOf(1L to 20), autoAdvanceOff = listOf(2L))

        assertThat(room.isAutoAdvance(1L)).isTrue()
        assertThat(room.isAutoAdvance(2L)).isFalse()
    }

    @Test
    fun `세트를 바꾸면 자동 넘김 끔 목록도 비워져 전부 기본(켬)으로 돌아간다`() {
        val room = room(questionSetId = 10L)
        room.overrideQuestionTimes(mapOf(1L to 20), autoAdvanceOff = listOf(1L, 3L))

        room.update("제목", null, null, 11L, null, false, null)

        assertThat(room.questionAutoAdvanceOff).isNull()
        assertThat(room.isAutoAdvance(1L)).isTrue()
    }

    @Test
    fun `문항별 시간은 대기 중일 때만 덮어쓸 수 있다`() {
        val room = room().apply { start() }

        assertThatThrownBy { room.overrideQuestionTimes(mapOf(1L to 20)) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.CONFLICT)
    }

    @Test
    fun `세트를 바꾸면 덮어쓴 시간이 비워지고 같은 세트면 남는다`() {
        val room = room(questionSetId = 10L).apply { overrideQuestionTimes(mapOf(1L to 20)) }

        // 같은 세트로 다른 항목만 고치면 시간은 그대로다
        room.update("제목", null, null, 10L, null, false, null)
        assertThat(room.questionTimeOverrides).containsEntry(1L, 20)

        // 예전 세트의 문항 id 를 가리키던 값은 의미가 없다
        room.update("제목", null, null, 11L, null, false, null)
        assertThat(room.questionTimeOverrides).isNull()
    }

    private fun room(maxParticipants: Int? = null, questionSetId: Long? = null) = Room(
        hostUserId = 1L,
        pin = "123456",
        type = RoomType.FREE,
        title = "CS 면접 대비",
        questionSetId = questionSetId,
        maxParticipants = maxParticipants,
    )
}
