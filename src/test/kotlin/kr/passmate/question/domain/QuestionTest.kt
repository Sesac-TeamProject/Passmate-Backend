package kr.passmate.question.domain

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class QuestionTest {

    @Test
    fun `객관식은 보기가 2개 이상이어야 한다`() {
        assertThatThrownBy { question(type = QuestionType.MCQ, choices = listOf("하나"), answer = "하나") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INVALID_QUESTION)
    }

    @Test
    fun `객관식 정답은 보기 중 하나여야 한다`() {
        assertThatThrownBy {
            question(type = QuestionType.MCQ, choices = listOf("가", "나"), answer = "다")
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INVALID_QUESTION)
    }

    @Test
    fun `OX 정답은 O 또는 X 만 된다`() {
        assertThatCode { question(type = QuestionType.OX, answer = "O") }.doesNotThrowAnyException()

        assertThatThrownBy { question(type = QuestionType.OX, answer = "예") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `서술형은 모범답안이 있어야 한다`() {
        assertThatThrownBy { question(type = QuestionType.ESSAY, answer = " ") }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INVALID_QUESTION)
    }

    @Test
    fun `제한시간과 배점은 범위를 벗어날 수 없다`() {
        assertThatThrownBy { question(timeLimitSec = 1) }.isInstanceOf(BusinessException::class.java)
        assertThatThrownBy { question(timeLimitSec = 601) }.isInstanceOf(BusinessException::class.java)
        assertThatThrownBy { question(points = 0) }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `수정할 때도 같은 규칙을 검사한다`() {
        val q = question(type = QuestionType.MCQ, choices = listOf("가", "나"), answer = "가")

        assertThatThrownBy {
            q.edit(QuestionType.MCQ, "바뀐 지문", listOf("가", "나"), "다", null, null, null, 30, 100)
        }.isInstanceOf(BusinessException::class.java)
    }

    private fun question(
        type: QuestionType = QuestionType.MCQ,
        choices: List<String>? = listOf("가", "나", "다"),
        answer: String? = "가",
        timeLimitSec: Int = 30,
        points: Int = 100,
    ) = Question(
        setId = 1L,
        orderNo = 1,
        type = type,
        content = "지문",
        choices = choices,
        answer = answer,
        timeLimitSec = timeLimitSec,
        points = points,
        source = QuestionSource.MANUAL,
    )
}
