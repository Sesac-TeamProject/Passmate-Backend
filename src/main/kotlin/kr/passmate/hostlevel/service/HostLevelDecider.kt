package kr.passmate.hostlevel.service

import kr.passmate.hostlevel.config.HostLevelProperties
import org.springframework.stereotype.Component

/** 등급 판정에 쓰는 집계값 묶음. */
data class GradeMetrics(
    /** 방 운영 횟수 — 확정 세트로 시작해서 종료까지 간 방 */
    val roomsHosted: Int,
    val totalStudents: Int,
    val avgRating: Double?,
    val ratingCount: Int,
    /** 유지 판정 기간 안의 활동 횟수 */
    val activeInWindow: Int,
)

/**
 * 등급표를 읽어 레벨 하나를 정하는 순수 판정기 (FR-045~047).
 *
 * DB 도 시계도 모른다 — 집계를 어떻게 구했는지와 "그래서 몇 레벨인가"를 갈라 두면
 * 방 40개를 실제로 만들지 않고도 등급표 전체를 시험할 수 있다.
 */
@Component
class HostLevelDecider(
    private val properties: HostLevelProperties,
) {

    /**
     * [currentLevel] 에서 [metrics] 로 도달하는 등급.
     *
     * [everEvaluated] 가 false 면 유지 판정을 건너뛴다 — 방금 승급한 프로필에
     * "최근 30일 활동이 모자란다"를 들이대면 오르자마자 떨어진다.
     */
    fun decide(currentLevel: Int, metrics: GradeMetrics, everEvaluated: Boolean): Int {
        val promoted = promote(currentLevel, metrics)
        return if (everEvaluated) demote(promoted, metrics) else promoted
    }

    /**
     * 아래에서부터 조건을 채우는 만큼 오른다. **한 번에 여러 단계를 오를 수 있지만**
     * (판정이 밀렸다고 한 계단씩만 올리면 배치를 여러 번 돌려야 따라잡힌다),
     * **중간 등급을 건너뛰지는 못한다.**
     *
     * 등급은 사다리다 — 조건을 못 넘긴 칸에서 멈춘다. 건너뛰게 두면
     * 방·학생 수만 채운 평점 3.0 짜리 호스트가 "검증된 운영자"(Lv.3, 별점 4.0)를
     * 지나쳐 마스터가 되고, 별점 관문이 아무것도 막지 못한다.
     */
    private fun promote(currentLevel: Int, metrics: GradeMetrics): Int {
        var reached = properties.lowest.level
        for (rule in properties.levels.sortedBy { it.level }) {
            if (rule.level <= currentLevel) {
                reached = rule.level
                continue
            }
            if (!meetsPromotion(rule, metrics)) break
            reached = rule.level
        }
        return maxOf(reached, currentLevel)
    }

    private fun meetsPromotion(rule: HostLevelProperties.Rule, metrics: GradeMetrics): Boolean {
        if (metrics.roomsHosted < rule.roomsHosted) return false
        if (metrics.totalStudents < rule.totalStudents) return false
        val required = rule.minAvgRating ?: return true
        // 표본이 모자라면 "별점이 낮다"가 아니라 "아직 판정할 수 없다"로 다룬다(FR-046)
        if (metrics.ratingCount < properties.ratingSampleMin) return false
        return (metrics.avgRating ?: 0.0) >= required
    }

    /**
     * 유지 조건 미달이면 **한 단계만** 내린다(FR-047).
     * 한 번의 판정으로 Lv.5 가 Lv.3 이 되면 되돌릴 길이 사실상 없다.
     */
    private fun demote(level: Int, metrics: GradeMetrics): Int {
        val rule = properties.ruleOf(level)
        if (!rule.demotable) return level
        if (meetsMaintenance(rule, metrics)) return level
        return (level - 1).coerceAtLeast(properties.lowest.level)
    }

    fun meetsMaintenance(rule: HostLevelProperties.Rule, metrics: GradeMetrics): Boolean {
        val sessions = rule.maintainSessions ?: return true
        if (metrics.activeInWindow < sessions) return false
        val required = rule.maintainAvgRating ?: return true
        // 표본 미달은 승급을 보류시킬 뿐, 이미 오른 등급을 떨어뜨리는 근거는 되지 않는다
        if (metrics.ratingCount < properties.ratingSampleMin) return true
        return (metrics.avgRating ?: 0.0) >= required
    }
}
