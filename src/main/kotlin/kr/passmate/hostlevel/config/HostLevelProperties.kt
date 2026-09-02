package kr.passmate.hostlevel.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 호스트 등급표 (기능 명세서 §7.1 FR-045). 숫자를 코드에 박지 않는다 —
 * 등급 기준은 운영하면서 조정되는 값이고, 그때마다 배포할 이유가 없다.
 *
 * 레벨 규칙은 스칼라 몇 개가 아니라 표라서 `PolicyProperties` 대신 여기에 둔다.
 */
@ConfigurationProperties(prefix = "passmate.host-level")
data class HostLevelProperties(
    /** 별점 조건을 판정하려면 평가가 최소 이만큼 쌓여야 한다. 미달이면 승급 보류(FR-046) */
    val ratingSampleMin: Int,
    /** Lv.4~5 유지 판정 주기(일). 이 기간의 활동·별점을 본다(FR-047) */
    val maintenanceDays: Long,
    /** Lv.1 부터 순서대로. Lv.1 은 가입 시 기본 등급이라 승급 조건이 사실상 시작점이다 */
    val levels: List<Rule>,
) {
    data class Rule(
        val level: Int,
        val name: String,
        /** 이 등급이 되기 위한 방 운영 횟수 (확정 세트로 시작~종료까지 간 방) */
        val roomsHosted: Int,
        /** 누적 학생 수 */
        val totalStudents: Int,
        /** 평균 별점 하한. null 이면 별점을 보지 않는다 */
        val minAvgRating: Double? = null,
        /** 유지 판정 기간의 최소 활동 횟수. null 이면 영구 등급(하락 없음) */
        val maintainSessions: Int? = null,
        /** 유지 판정의 평균 별점 하한 */
        val maintainAvgRating: Double? = null,
        /** 이 등급에서 열리는 기능. 화면이 그대로 띄운다 */
        val unlock: String? = null,
    ) {
        /** 유지 조건이 있으면 하락할 수 있는 등급이다(Lv.4~5). */
        val demotable: Boolean get() = maintainSessions != null
    }

    val lowest: Rule get() = levels.minBy { it.level }

    fun ruleOf(level: Int): Rule = levels.firstOrNull { it.level == level } ?: lowest

    fun nextOf(level: Int): Rule? = levels.filter { it.level > level }.minByOrNull { it.level }
}
