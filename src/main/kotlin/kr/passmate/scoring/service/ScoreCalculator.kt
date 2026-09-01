package kr.passmate.scoring.service

import kr.passmate.question.domain.QuestionType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/** 채점 결과. */
data class ScoreResult(
    /** 서술형은 채점 전이라 null */
    val isCorrect: Boolean?,
    val baseScore: Int,
    val speedBonus: Int,
) {
    val total: Int get() = baseScore + speedBonus
}

/**
 * 점수 공식 (기능 명세서 FR-024).
 *
 * - 정답: 배점 100% + 남은 시간 비율에 비례한 속도 보너스(최대 배점의 +50%)
 *   예) 100점 문항을 제한시간 절반 시점에 정답 → 100 + 100 × 0.5 × 0.5 = 125
 * - 오답·미제출: 0점
 * - 서술형: 제출 시 배점을 **속도 보너스 없이** 잠정 부여해 즉시 랭킹에 반영하고,
 *   AI 분석·첨삭이 확정되면 보정한다. 서술형에 속도 보너스를 주면 빨리 대충 쓴 쪽이 유리해진다
 */
@Component
class ScoreCalculator {

    fun score(
        type: QuestionType,
        points: Int,
        submitted: String,
        answer: String?,
        remainingRatio: BigDecimal,
    ): ScoreResult = when (type) {
        QuestionType.ESSAY -> ScoreResult(isCorrect = null, baseScore = points, speedBonus = 0)

        QuestionType.MCQ, QuestionType.OX -> {
            val correct = answer != null && submitted.trim().equals(answer.trim(), ignoreCase = false)
            if (!correct) {
                ScoreResult(isCorrect = false, baseScore = 0, speedBonus = 0)
            } else {
                val bonus = BigDecimal(points)
                    .multiply(MAX_BONUS_RATE)
                    .multiply(remainingRatio.coerceIn(BigDecimal.ZERO, BigDecimal.ONE))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toInt()
                ScoreResult(isCorrect = true, baseScore = points, speedBonus = bonus)
            }
        }
    }

    companion object {
        /** 속도 보너스 상한 — 배점의 50% */
        private val MAX_BONUS_RATE = BigDecimal("0.5")
    }
}
