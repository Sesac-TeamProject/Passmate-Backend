package kr.passmate.hostlevel.service

import kr.passmate.hostlevel.config.HostLevelProperties
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.dto.GradeMaintenance
import kr.passmate.hostlevel.dto.GradeRequirement
import kr.passmate.hostlevel.dto.HostGradeResponse
import kr.passmate.hostlevel.repository.HostProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 명성 조회 (FR-045 · FR-048, W-09 · M-09).
 *
 * 등급 기준을 화면이 들고 있지 않도록 **조건과 진행도를 서버가 계산해서** 내려준다.
 */
@Service
@Transactional(readOnly = true)
class HostGradeQueryService(
    private val properties: HostLevelProperties,
    private val hostProfileRepository: HostProfileRepository,
    private val hostGradeService: HostGradeService,
) {

    /**
     * 내 등급. 프로필이 아직 없으면 그 자리에서 만든다 —
     * 세션을 한 번도 안 한 회원에게 404 를 주면 화면이 빈손이 된다.
     */
    @Transactional
    fun myGrade(userId: Long): HostGradeResponse =
        toResponse(hostProfileRepository.findByUserId(userId) ?: hostGradeService.evaluate(userId))

    /** 여러 사람의 등급을 한 번에. 목록 화면이 사람마다 조회하지 않도록. */
    fun levelsOf(userIds: Collection<Long>): Map<Long, Int> =
        hostProfileRepository.findAllByUserIdIn(userIds).associate { it.userId to it.level }

    /**
     * 여러 사람의 등급. **아직 판정된 적 없는 회원은 기본 등급으로 채운다** —
     * 목록 화면에서 등급 칸만 비면 "등급 없음"처럼 읽힌다.
     */
    fun levelsOrDefault(userIds: Collection<Long>): Map<Long, Int> {
        val known = levelsOf(userIds)
        return userIds.associateWith { known[it] ?: properties.lowest.level }
    }

    fun findProfile(userId: Long): HostProfile? = hostProfileRepository.findByUserId(userId)

    fun toResponse(profile: HostProfile): HostGradeResponse {
        val current = properties.ruleOf(profile.level)
        val next = properties.nextOf(profile.level)
        val requirements = next?.let { requirementsOf(it, profile) }.orEmpty()

        return HostGradeResponse(
            level = profile.level,
            levelName = current.name,
            levelAchievedAt = profile.levelAchievedAt,
            roomsHosted = profile.roomsHosted,
            totalStudents = profile.totalStudents,
            avgRating = profile.avgRating?.toDouble(),
            ratingCount = profile.ratingCount,
            nextLevel = next?.level,
            nextLevelName = next?.name,
            nextRequirements = requirements,
            nextLevelProgress = requirements.takeIf { it.isNotEmpty() }
                ?.let { rows -> round(rows.sumOf { it.ratio } / rows.size) },
            ratingSamplePending = next?.minAvgRating != null && profile.ratingCount < properties.ratingSampleMin,
            maintenance = maintenanceOf(current, profile),
            unlocked = properties.levels
                .filter { it.level <= profile.level }
                .sortedBy { it.level }
                .mapNotNull { it.unlock },
            lastEvaluatedAt = profile.lastEvaluatedAt,
        )
    }

    private fun requirementsOf(rule: HostLevelProperties.Rule, profile: HostProfile): List<GradeRequirement> =
        buildList {
            add(requirement("ROOMS_HOSTED", "방 운영 횟수", profile.roomsHosted.toDouble(), rule.roomsHosted.toDouble()))
            if (rule.totalStudents > 0) {
                add(requirement("TOTAL_STUDENTS", "누적 학생 수", profile.totalStudents.toDouble(), rule.totalStudents.toDouble()))
            }
            rule.minAvgRating?.let {
                add(requirement("AVG_RATING", "평균 별점", profile.avgRating?.toDouble() ?: 0.0, it))
            }
        }

    private fun maintenanceOf(rule: HostLevelProperties.Rule, profile: HostProfile): GradeMaintenance? {
        val requiredSessions = rule.maintainSessions ?: return null
        val avgRating = profile.avgRating?.toDouble()
        val ratingMet = rule.maintainAvgRating == null ||
            profile.ratingCount < properties.ratingSampleMin ||
            (avgRating ?: 0.0) >= rule.maintainAvgRating
        return GradeMaintenance(
            windowDays = properties.maintenanceDays,
            sessionsInWindow = profile.activeLast30d,
            requiredSessions = requiredSessions,
            avgRating = avgRating,
            requiredAvgRating = rule.maintainAvgRating,
            met = profile.activeLast30d >= requiredSessions && ratingMet,
            nextEvaluationAt = profile.nextEvaluationAt,
        )
    }

    private fun requirement(type: String, label: String, current: Double, target: Double) =
        GradeRequirement(type, label, round(current), round(target), current >= target)

    /** 조건별 달성 비율. 1을 넘지 않게 잘라 평균이 100%를 넘지 않게 한다. */
    private val GradeRequirement.ratio: Double
        get() = if (target <= 0) 1.0 else (current / target).coerceAtMost(1.0)

    private fun round(value: Double) = Math.round(value * 100.0) / 100.0
}
