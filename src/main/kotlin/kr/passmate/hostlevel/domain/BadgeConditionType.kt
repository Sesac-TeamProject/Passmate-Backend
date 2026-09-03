package kr.passmate.hostlevel.domain

/**
 * 뱃지 획득 조건의 종류 (FR-048). `badge.condition_type` 문자열과 1:1 이다.
 *
 * 조건값 컬럼이 INT 라 별점만 10배로 담는다(45 = 4.5) — [scale] 이 그걸 되돌린다.
 */
enum class BadgeConditionType(val scale: Double = 1.0) {
    /** 방 운영 횟수 */
    ROOMS_HOSTED,

    /** 누적 학생 수 */
    TOTAL_STUDENTS,

    /** 평균 별점. condition_value 는 10배로 들어 있다 */
    AVG_RATING(scale = 0.1),

    /** 받은 평가 수 */
    RATING_COUNT,

    /** 연속 활동 일수 */
    ACTIVE_STREAK_DAYS,

    /** 개설한 유료 방 수 */
    PAID_ROOMS,

    /** AI 로 만든 문제 세트 수 */
    AI_QUESTION_SETS,
    ;

    fun target(conditionValue: Int): Double = conditionValue * scale

    companion object {
        /** 모르는 조건이 DB 에 들어 있어도 조회가 통째로 죽지 않게 null 로 넘긴다. */
        fun of(raw: String?): BadgeConditionType? = entries.firstOrNull { it.name == raw }
    }
}
