package kr.passmate.scoring

import kr.passmate.question.domain.QuestionType
import kr.passmate.scoring.service.ScoreCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** 점수 공식 경계 (FR-024). 명세서 예시를 그대로 검증한다. */
class ScoreCalculatorTest {

    private val calculator = ScoreCalculator()

    @Test
    fun `명세서 예시 - 100점 문항을 제한시간 절반에 맞히면 125점`() {
        val r = calculator.score(QuestionType.MCQ, 100, "가", "가", BigDecimal("0.5"))

        assertThat(r.isCorrect).isTrue()
        assertThat(r.baseScore).isEqualTo(100)
        assertThat(r.speedBonus).isEqualTo(25)
        assertThat(r.total).isEqualTo(125)
    }

    @Test
    fun `시작하자마자 맞히면 배점의 150퍼센트`() {
        val r = calculator.score(QuestionType.MCQ, 100, "가", "가", BigDecimal.ONE)

        assertThat(r.total).isEqualTo(150)
    }

    @Test
    fun `만료 직전에 맞히면 속도 보너스가 0 이라 배점만 받는다`() {
        val r = calculator.score(QuestionType.MCQ, 100, "가", "가", BigDecimal.ZERO)

        assertThat(r.speedBonus).isZero()
        assertThat(r.total).isEqualTo(100)
    }

    @Test
    fun `오답은 시간이 아무리 남아도 0점이다`() {
        val r = calculator.score(QuestionType.MCQ, 100, "나", "가", BigDecimal.ONE)

        assertThat(r.isCorrect).isFalse()
        assertThat(r.baseScore).isZero()
        assertThat(r.speedBonus).isZero()
        assertThat(r.total).isZero()
    }

    @Test
    fun `OX 도 같은 공식을 쓴다`() {
        assertThat(calculator.score(QuestionType.OX, 200, "O", "O", BigDecimal("0.5")).total).isEqualTo(250)
        assertThat(calculator.score(QuestionType.OX, 200, "X", "O", BigDecimal("0.5")).total).isZero()
    }

    @Test
    fun `서술형은 속도 보너스 없이 배점을 잠정 부여하고 정오는 미정이다`() {
        // 빨리 대충 쓴 쪽이 유리해지면 안 되므로 보너스를 주지 않는다
        val r = calculator.score(QuestionType.ESSAY, 100, "제 생각에는...", "모범답안", BigDecimal.ONE)

        assertThat(r.isCorrect).isNull()
        assertThat(r.baseScore).isEqualTo(100)
        assertThat(r.speedBonus).isZero()
        assertThat(r.total).isEqualTo(100)
    }

    @Test
    fun `앞뒤 공백은 무시하고 대소문자는 구분한다`() {
        assertThat(calculator.score(QuestionType.MCQ, 100, "  가  ", "가", BigDecimal.ZERO).isCorrect).isTrue()
        assertThat(calculator.score(QuestionType.MCQ, 100, "TCP", "tcp", BigDecimal.ZERO).isCorrect).isFalse()
    }
}
