package kr.passmate.hostlevel.service

import kr.passmate.hostlevel.domain.Badge
import kr.passmate.hostlevel.domain.BadgeConditionType
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.domain.UserBadge
import kr.passmate.hostlevel.repository.BadgeRepository
import kr.passmate.hostlevel.repository.UserBadgeRepository
import kr.passmate.question.domain.ContentSource
import kr.passmate.question.repository.QuestionSetRepository
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.domain.RoomType
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 뱃지 획득 처리 (FR-048).
 *
 * 등급 판정과 같은 시점에 돈다 — 조건이 같은 집계를 보기 때문에 따로 돌릴 이유가 없다.
 * 못 딴 뱃지도 행을 만들어 진행도를 담는다. 화면이 "30일 연속 활동 12/30"을 그린다.
 */
@Service
class BadgeService(
    private val badgeRepository: BadgeRepository,
    private val userBadgeRepository: UserBadgeRepository,
    private val roomRepository: RoomRepository,
    private val questionSetRepository: QuestionSetRepository,
) {

    @Transactional
    fun refresh(profile: HostProfile, at: LocalDateTime = LocalDateTime.now()): List<UserBadge> {
        val badges = badgeRepository.findAll()
        if (badges.isEmpty()) return emptyList()

        val owned = userBadgeRepository.findAllByUserId(profile.userId).associateBy { it.badgeId }
        // 필요한 조건만 센다 — 뱃지가 8개라고 매번 여덟 번 조회할 이유는 없다
        val extras = extraMetrics(profile.userId, badges, at)

        return badges.map { badge ->
            val row = owned[badge.id]
                ?: userBadgeRepository.save(UserBadge(userId = profile.userId, badgeId = badge.id))
            val current = currentOf(badge, profile, extras)
            val target = badge.target
            row.update(
                progress = current.toInt(),
                met = target != null && current >= target,
                at = at,
            )
            row
        }
    }

    /** 뱃지마다 지금 값이 얼마인지. HostProfile 에 이미 있는 값은 다시 세지 않는다. */
    private fun currentOf(badge: Badge, profile: HostProfile, extras: ExtraMetrics): Double =
        when (badge.condition) {
            BadgeConditionType.ROOMS_HOSTED -> profile.roomsHosted.toDouble()
            BadgeConditionType.TOTAL_STUDENTS -> profile.totalStudents.toDouble()
            BadgeConditionType.AVG_RATING -> profile.avgRating?.toDouble() ?: 0.0
            BadgeConditionType.RATING_COUNT -> profile.ratingCount.toDouble()
            BadgeConditionType.ACTIVE_STREAK_DAYS -> extras.activeStreakDays.toDouble()
            BadgeConditionType.PAID_ROOMS -> extras.paidRooms.toDouble()
            BadgeConditionType.AI_QUESTION_SETS -> extras.aiQuestionSets.toDouble()
            null -> 0.0
        }

    private fun extraMetrics(userId: Long, badges: List<Badge>, at: LocalDateTime): ExtraMetrics {
        val needed = badges.mapNotNull { it.condition }.toSet()
        return ExtraMetrics(
            activeStreakDays = if (BadgeConditionType.ACTIVE_STREAK_DAYS in needed) {
                activeStreakDays(userId, at.toLocalDate())
            } else {
                0
            },
            paidRooms = if (BadgeConditionType.PAID_ROOMS in needed) {
                roomRepository.countByHostUserIdAndType(userId, RoomType.PAID).toInt()
            } else {
                0
            },
            aiQuestionSets = if (BadgeConditionType.AI_QUESTION_SETS in needed) {
                questionSetRepository
                    .countByOwnerUserIdAndSourceAndDeletedAtIsNull(userId, ContentSource.AI).toInt()
            } else {
                0
            },
        )
    }

    /**
     * 지금까지 이어진 연속 활동 일수.
     *
     * 오늘 아직 세션을 안 했어도 어제까지 이어졌으면 연속으로 친다 —
     * 자정을 넘겼다고 기록이 끊기면 아침에 확인한 사람마다 0을 본다.
     */
    private fun activeStreakDays(userId: Long, today: LocalDate): Int {
        val dates = roomRepository.findEndedDates(userId, RoomStatus.ENDED)
            .map { it.toLocalDate() }
            .distinct()
            .sortedDescending()
        if (dates.isEmpty()) return 0

        val start = dates.first()
        if (start < today.minusDays(1)) return 0

        var streak = 1
        var cursor = start
        dates.drop(1).forEach { date ->
            if (date == cursor.minusDays(1)) {
                streak++
                cursor = date
            } else {
                return streak
            }
        }
        return streak
    }

    private data class ExtraMetrics(
        val activeStreakDays: Int,
        val paidRooms: Int,
        val aiQuestionSets: Int,
    )
}
