package kr.passmate.hostlevel.service

import kr.passmate.hostlevel.domain.Badge
import kr.passmate.hostlevel.domain.UserBadge
import kr.passmate.hostlevel.dto.BadgeCollectionResponse
import kr.passmate.hostlevel.dto.BadgeResponse
import kr.passmate.hostlevel.repository.BadgeRepository
import kr.passmate.hostlevel.repository.UserBadgeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 뱃지 조회 (FR-048, M-09 · M-10).
 */
@Service
@Transactional(readOnly = true)
class BadgeQueryService(
    private val badgeRepository: BadgeRepository,
    private val userBadgeRepository: UserBadgeRepository,
    private val hostGradeQueryService: HostGradeQueryService,
    private val hostGradeService: HostGradeService,
) {

    /**
     * 내 뱃지 컬렉션. 못 딴 것도 전부 내려간다 — 컬렉션은 빈칸이 보여야 컬렉션이다.
     *
     * 아직 판정된 적 없는 회원은 그 자리에서 판정한다. 안 그러면 진행도가 전부 0 으로 보인다.
     */
    @Transactional
    fun myBadges(userId: Long): BadgeCollectionResponse {
        if (hostGradeQueryService.findProfile(userId) == null) hostGradeService.evaluate(userId)
        return collection(userId, achievedOnly = false)
    }

    /** 공개 프로필용 — 획득한 것만. 남에게 "무엇을 못 땄는지"까지 보일 이유는 없다. */
    fun achievedBadges(userId: Long): List<BadgeResponse> =
        collection(userId, achievedOnly = true).badges

    private fun collection(userId: Long, achievedOnly: Boolean): BadgeCollectionResponse {
        val badges = badgeRepository.findAll().associateBy { it.id }
        val rows = if (achievedOnly) {
            userBadgeRepository.findAllByUserIdAndAchievedAtIsNotNull(userId)
        } else {
            userBadgeRepository.findAllByUserId(userId)
        }
        val byBadgeId = rows.associateBy { it.badgeId }

        val entries = badges.values
            .filter { !achievedOnly || byBadgeId[it.id]?.achieved == true }
            .map { badge -> toResponse(badge, byBadgeId[badge.id]) }
            // 딴 것 먼저, 그 안에서는 최근 획득 순 — 자랑거리가 위로 온다
            .sortedWith(
                compareByDescending<BadgeResponse> { it.achieved }
                    .thenByDescending { it.achievedAt }
                    .thenBy { it.code },
            )

        return BadgeCollectionResponse(
            achievedCount = entries.count { it.achieved },
            totalCount = if (achievedOnly) entries.size else badges.size,
            badges = entries,
        )
    }

    private fun toResponse(badge: Badge, row: UserBadge?) = BadgeResponse(
        code = badge.code,
        name = badge.name,
        description = badge.description,
        iconUrl = badge.iconUrl,
        achieved = row?.achieved ?: false,
        achievedAt = row?.achievedAt,
        progress = row?.progress ?: 0,
        target = badge.target,
    )
}
