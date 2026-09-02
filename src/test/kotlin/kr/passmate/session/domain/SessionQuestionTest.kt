package kr.passmate.session.domain

import kr.passmate.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class SessionQuestionTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 1, 10, 0, 0)

    @Test
    fun `시작하면 서버가 endsAt 을 발급한다`() {
        val sq = sessionQuestion(timeLimitSec = 30).apply { start(now) }

        assertThat(sq.startedAt).isEqualTo(now)
        assertThat(sq.endsAt).isEqualTo(now.plusSeconds(30))
        assertThat(sq.isRunning).isTrue()
    }

    @Test
    fun `두 번 시작할 수 없다`() {
        val sq = sessionQuestion().apply { start(now) }

        assertThatThrownBy { sq.start(now) }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `마감은 멱등이다 - 타이머와 호스트 조작이 겹쳐도 한 번만 닫힌다`() {
        val sq = sessionQuestion().apply { start(now) }
        sq.end(10, 6, mapOf("가" to 6, "나" to 4), now.plusSeconds(30))
        sq.end(99, 99, mapOf("가" to 99), now.plusSeconds(40))

        assertThat(sq.submitCount).isEqualTo(10)
        assertThat(sq.correctCount).isEqualTo(6)
        assertThat(sq.endedAt).isEqualTo(now.plusSeconds(30))
        assertThat(sq.correctRate).isEqualByComparingTo(BigDecimal("60.00"))
    }

    @Test
    fun `아무도 제출하지 않으면 정답률은 0 이다`() {
        val sq = sessionQuestion().apply { start(now) }
        sq.end(0, 0, emptyMap(), now.plusSeconds(30))

        assertThat(sq.correctRate).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `제한시간이 지나면 만료로 본다`() {
        val sq = sessionQuestion(timeLimitSec = 30).apply { start(now) }

        assertThat(sq.isExpired(now.plusSeconds(29))).isFalse()
        assertThat(sq.isExpired(now.plusSeconds(31))).isTrue()
    }

    @Test
    fun `마감된 문항은 더 이상 만료 대상이 아니다`() {
        val sq = sessionQuestion().apply { start(now) }
        sq.end(0, 0, emptyMap(), now.plusSeconds(10))

        assertThat(sq.isExpired(now.plusSeconds(100))).isFalse()
    }

    @Test
    fun `남은 시간 비율은 절반 시점에 0점5 이고 만료 후에는 0 이다`() {
        val sq = sessionQuestion(timeLimitSec = 30).apply { start(now) }

        assertThat(sq.remainingRatio(now.plusSeconds(15)).toDouble()).isEqualTo(0.5)
        assertThat(sq.remainingRatio(now)).isEqualByComparingTo(BigDecimal.ONE)
        assertThat(sq.remainingRatio(now.plusSeconds(31))).isEqualByComparingTo(BigDecimal.ZERO)
    }

    private fun sessionQuestion(timeLimitSec: Int = 30) =
        SessionQuestion(roomId = 1L, questionId = 1L, orderNo = 1, timeLimitSec = timeLimitSec)
}
