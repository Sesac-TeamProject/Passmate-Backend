package kr.passmate.question.domain

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class QuestionSetTest {

    @Test
    fun `문항이 없으면 확정할 수 없다`() {
        assertThatThrownBy { set().confirm() }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.QUESTION_SET_EMPTY)
    }

    @Test
    fun `확정하면 집계가 남고 상태가 바뀐다`() {
        val set = set()
        set.refreshStats(listOf(question(points = 100), question(points = 200)))
        set.confirm()

        assertThat(set.status).isEqualTo(QuestionSetStatus.CONFIRMED)
        assertThat(set.questionCount).isEqualTo(2)
        assertThat(set.totalPoints).isEqualTo(300)
        assertThat(set.estimatedSeconds).isEqualTo(60)
        assertThat(set.confirmedAt).isNotNull()
    }

    @Test
    fun `확정된 세트는 제목도 못 바꾼다`() {
        val set = confirmedSet()

        assertThatThrownBy { set.edit("새 제목", null) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.QUESTION_SET_ALREADY_CONFIRMED)
    }

    @Test
    fun `두 번 확정할 수 없다`() {
        assertThatThrownBy { confirmedSet().confirm() }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.QUESTION_SET_ALREADY_CONFIRMED)
    }

    @Test
    fun `소유자가 아니면 막는다`() {
        assertThatThrownBy { set().verifyOwner(999L) }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.NOT_QUESTION_SET_OWNER)
    }

    @Test
    fun `문항 출처가 섞이면 세트 출처는 MIXED 다`() {
        val set = set()

        set.refreshStats(listOf(question(source = QuestionSource.AI)))
        assertThat(set.source).isEqualTo(ContentSource.AI)

        set.refreshStats(listOf(question(source = QuestionSource.AI), question(source = QuestionSource.MANUAL)))
        assertThat(set.source).isEqualTo(ContentSource.MIXED)

        set.refreshStats(emptyList())
        assertThat(set.source).isNull()
    }

    private fun set() = QuestionSet(ownerUserId = 1L, title = "CS 면접")

    private fun confirmedSet() = set().apply {
        refreshStats(listOf(question()))
        confirm()
    }

    private fun question(points: Int = 100, source: QuestionSource = QuestionSource.MANUAL) = Question(
        setId = 1L,
        orderNo = 1,
        type = QuestionType.OX,
        content = "지문",
        answer = "O",
        timeLimitSec = 30,
        points = points,
        source = source,
    )
}
