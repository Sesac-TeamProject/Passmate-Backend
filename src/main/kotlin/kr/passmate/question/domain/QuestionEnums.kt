package kr.passmate.question.domain

/** 세트 상태. 확정하면 불변이 되고, 확정된 세트만 세션에 출제할 수 있다. */
enum class QuestionSetStatus {
    DRAFT,
    CONFIRMED,
}

/** 세트 안 문항이 어떻게 만들어졌는지. 섞여 있으면 MIXED. */
enum class ContentSource {
    AI,
    MANUAL,
    MIXED,
    ;

    companion object {
        /** 문항들의 출처를 합쳐 세트 출처를 정한다. */
        fun of(sources: Collection<QuestionSource>): ContentSource? = when {
            sources.isEmpty() -> null
            sources.all { it == QuestionSource.AI } -> AI
            sources.all { it == QuestionSource.MANUAL } -> MANUAL
            else -> MIXED
        }
    }
}

enum class QuestionSource {
    AI,
    MANUAL,
}

enum class QuestionType(
    /**
     * 제한시간을 정하지 않았을 때의 기본값(초). 서술형은 쓰는 시간이 필요해 길다 —
     * W-02b "서술형은 기본 90초로 잡혀 있어요". 유형 무관 30초 하나로 두면 AI 생성 서술형이 30초가 된다.
     */
    val defaultTimeLimitSec: Int,
) {
    /** 객관식 — 보기 필요 */
    MCQ(30),

    /** O/X */
    OX(30),

    /** 서술형 — 정답 텍스트는 채점 기준으로 쓰고 AI 가 분석한다 */
    ESSAY(90),
}

enum class Difficulty {
    EASY,
    NORMAL,
    HARD,
}
