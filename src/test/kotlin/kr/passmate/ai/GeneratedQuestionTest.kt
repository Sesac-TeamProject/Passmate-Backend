package kr.passmate.ai

import kr.passmate.ai.client.AiCallException
import kr.passmate.ai.client.GeneratedQuestion
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.QuestionType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * AI 결과 검증. JSON Schema 로는 막을 수 없는 조건들이라 여기서 본다.
 * 여기서 걸러야 재시도가 의미를 갖고, 사용자에게 400(입력 오류)으로 잘못 보이지 않는다.
 */
class GeneratedQuestionTest {

    @Test
    fun `객관식 정답이 보기 안에 없으면 재시도 대상이다`() {
        val question = mcq(choices = listOf("가", "나"), answer = "다")

        assertThatThrownBy { question.verifyConsistent() }
            .isInstanceOf(AiCallException::class.java)
            .satisfies({ assertThat((it as AiCallException).retryable).isTrue() })
    }

    @Test
    fun `객관식 보기가 하나뿐이면 거부한다`() {
        assertThatThrownBy { mcq(choices = listOf("가"), answer = "가").verifyConsistent() }
            .isInstanceOf(AiCallException::class.java)
    }

    @Test
    fun `OX 정답은 O 나 X 여야 한다`() {
        assertThatThrownBy { ox("참").verifyConsistent() }
            .isInstanceOf(AiCallException::class.java)
        assertThatCode { ox("O").verifyConsistent() }.doesNotThrowAnyException()
        assertThatCode { ox("X").verifyConsistent() }.doesNotThrowAnyException()
    }

    @Test
    fun `서술형 모범답안이 비면 채점 기준이 없어 거부한다`() {
        val essay = GeneratedQuestion(
            type = QuestionType.ESSAY,
            content = "설명하시오",
            choices = null,
            answer = "  ",
            explanation = null,
            difficulty = Difficulty.NORMAL,
        )

        assertThatThrownBy { essay.verifyConsistent() }
            .isInstanceOf(AiCallException::class.java)
    }

    @Test
    fun `지문이 비면 거부한다`() {
        assertThatThrownBy { ox("O", content = " ").verifyConsistent() }
            .isInstanceOf(AiCallException::class.java)
    }

    @Test
    fun `조건을 지킨 객관식은 통과한다`() {
        assertThatCode { mcq(listOf("가", "나", "다", "라"), "나").verifyConsistent() }
            .doesNotThrowAnyException()
    }

    private fun mcq(choices: List<String>, answer: String) = GeneratedQuestion(
        type = QuestionType.MCQ,
        content = "다음 중 옳은 것은?",
        choices = choices,
        answer = answer,
        explanation = null,
        difficulty = Difficulty.NORMAL,
    )

    private fun ox(answer: String, content: String = "TCP 는 연결지향이다") = GeneratedQuestion(
        type = QuestionType.OX,
        content = content,
        choices = null,
        answer = answer,
        explanation = null,
        difficulty = Difficulty.NORMAL,
    )
}
