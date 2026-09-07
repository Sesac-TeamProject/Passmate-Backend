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
    fun `객관식 보기가 정확히 4개가 아니면 재시도 대상이다`() {
        // 프론트가 보기 키를 A·B·C·D 로 고정해 4개가 아니면 화면이 깨진다(웹 버그 리포트 #3) —
        // 프롬프트도 "보기 4개"를 요구하므로 검증도 정확히 4개로 못박는다
        listOf(
            listOf("가"),
            listOf("가", "나"),
            listOf("가", "나", "다"),
            listOf("가", "나", "다", "라", "마"),
        ).forEach { choices ->
            assertThatThrownBy { mcq(choices = choices, answer = "가").verifyConsistent() }
                .isInstanceOf(AiCallException::class.java)
                .satisfies({ assertThat((it as AiCallException).retryable).isTrue() })
        }
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
    fun `서술형 문항 지문과 모범답안이 같으면 재시도 대상이다`() {
        // 모델이 모범답안을 지문에도 그대로 써 보낸 실제 사례(2026-09-08, 세트 6). 학생에게 답을 보여주는 셈이다
        val text = "힙 자료구조는 이진 트리의 특성을 가지며, 각 부모 노드는 자식 노드들보다 크거나 작아야 한다."

        assertThatThrownBy { essay(content = text, answer = "  $text \n").verifyConsistent() }
            .isInstanceOf(AiCallException::class.java)
            .satisfies({ assertThat((it as AiCallException).retryable).isTrue() })
    }

    @Test
    fun `서술형 지문이 질문이고 모범답안이 다르면 통과한다`() {
        assertThatCode {
            essay(content = "힙에 값을 추가하는 과정을 설명하시오.", answer = "마지막 위치에 삽입한 뒤 부모와 비교해 위로 올린다.")
                .verifyConsistent()
        }.doesNotThrowAnyException()
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

    private fun essay(content: String, answer: String) = GeneratedQuestion(
        type = QuestionType.ESSAY,
        content = content,
        choices = null,
        answer = answer,
        explanation = null,
        difficulty = Difficulty.NORMAL,
    )

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
