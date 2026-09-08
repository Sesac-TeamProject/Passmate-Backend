package kr.passmate.room.domain

import kr.passmate.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ParticipantTest {

    @Test
    fun `세션이 끝나면 최종 점수와 등수를 굳힌다`() {
        val participant = participant()

        participant.recordResult(totalScore = 121, finalRank = 1)

        assertThat(participant.totalScore).isEqualTo(121)
        assertThat(participant.finalRank).isEqualTo(1)
    }

    @Test
    fun `첨삭으로 점수가 바뀌면 다시 굳힐 수 있다`() {
        val participant = participant().apply { recordResult(121, 2) }

        participant.recordResult(totalScore = 221, finalRank = 1)

        assertThat(participant.totalScore).isEqualTo(221)
        assertThat(participant.finalRank).isEqualTo(1)
    }

    @Test
    fun `나간 사람만 다시 들어올 수 있다`() {
        assertThatThrownBy { participant().rejoin() }.isInstanceOf(BusinessException::class.java)
    }

    private fun participant() = Participant(roomId = 1L, nickname = "게스트", avatarId = "avatar-01", guestToken = "t")
}
