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
 * - 서술형: 제출 시점에는 **0점**이고, 선생님 첨삭이 보정 점수를 주면 그때 반영된다.
 *   예전에는 배점을 잠정 부여했지만 "잘 모르겠습니다"만 써도 만점을 받는 문제가 있었다
 *   (시나리오 테스트, 2026-09-08 반전). 자동 채점이 없는 유형에 점수를 미리 주지 않는다
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
        QuestionType.ESSAY -> ScoreResult(isCorrect = null, baseScore = 0, speedBonus = 0)

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
