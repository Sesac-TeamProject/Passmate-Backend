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

enum class QuestionType {
    /** 객관식 — 보기 필요 */
    MCQ,

    /** O/X */
    OX,

    /** 서술형 — 정답 텍스트는 채점 기준으로 쓰고 AI 가 분석한다 */
    ESSAY,
}

enum class Difficulty {
    EASY,
    NORMAL,
    HARD,
}
