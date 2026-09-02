package kr.passmate.hostlevel.service

import kr.passmate.common.event.SessionEndedEvent
import kr.passmate.hostlevel.config.HostLevelProperties
import kr.passmate.hostlevel.domain.HostProfile
import kr.passmate.hostlevel.dto.GradeEvaluationResult
import kr.passmate.hostlevel.repository.HostProfileRepository
import kr.passmate.rating.service.RoomRatingQueryService
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.repository.RoomRepository
import kr.passmate.room.service.RoomQueryService
import kr.passmate.room.service.RoomStatsService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/**
 * 등급 판정 (FR-045~047, SC-013).
 *
 * 이 서비스는 **집계를 모으고 결과를 저장**하는 일만 한다.
 * "그래서 몇 레벨인가"는 [HostLevelDecider] 가 정한다.
 */
@Service
class HostGradeService(
    private val properties: HostLevelProperties,
    private val decider: HostLevelDecider,
    private val roomRepository: RoomRepository,
    private val roomQueryService: RoomQueryService,
    private val roomStatsService: RoomStatsService,
    private val roomRatingQueryService: RoomRatingQueryService,
    private val hostProfileRepository: HostProfileRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 세션이 끝나면 그 호스트 등급을 다시 본다.
     *
     * "수동 개입 없이 자동 반영"(SC-013)이 요구라서 배치를 기다리지 않는다 —
     * 20번째 세션을 끝낸 호스트가 다음 배치까지 Lv.2 로 남아 있으면 안 된다.
     */
    @EventListener
    @Transactional
    fun onSessionEnded(event: SessionEndedEvent) {
        evaluate(roomQueryService.getRoom(event.roomId).hostUserId)
    }

    /** 호스트 한 명을 판정한다. 프로필이 없으면 이때 만든다. */
    @Transactional
    fun evaluate(userId: Long, now: LocalDateTime = LocalDateTime.now()): HostProfile {
        val profile = hostProfileRepository.findByUserId(userId)
            ?: hostProfileRepository.save(HostProfile(userId = userId))

        val metrics = collect(userId, now)
        profile.refreshMetrics(
            roomsHosted = metrics.roomsHosted,
            totalStudents = metrics.totalStudents,
            avgRating = metrics.avgRating?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP) },
            ratingCount = metrics.ratingCount,
            activeInWindow = metrics.activeInWindow,
        )

        val before = profile.level
        val after = decider.decide(before, metrics, everEvaluated = profile.lastEvaluatedAt != null)
        profile.applyLevel(after, now)
        profile.markEvaluated(
            now,
            if (properties.ruleOf(after).demotable) now.plusDays(properties.maintenanceDays) else null,
        )

        if (before != after) log.info("호스트 등급이 바뀌었다 userId={} {} -> {}", userId, before, after)
        return profile
    }

    /**
     * 세션을 진행한 적 있는 호스트 전부를 판정한다(관리자 수동 실행).
     * 한 번도 세션을 끝내지 않은 회원은 등급이 바뀔 근거가 없어 건너뛴다.
     */
    @Transactional
    fun evaluateAll(now: LocalDateTime = LocalDateTime.now()): GradeEvaluationResult =
        evaluateEach(roomRepository.findHostUserIdsWithEndedSession(RoomStatus.ENDED), now)

    @Transactional
    fun evaluateOne(userId: Long, now: LocalDateTime = LocalDateTime.now()): GradeEvaluationResult =
        evaluateEach(listOf(userId), now)

    private fun evaluateEach(userIds: List<Long>, now: LocalDateTime): GradeEvaluationResult {
        var promoted = 0
        var demoted = 0
        userIds.forEach { userId ->
            val before = hostProfileRepository.findByUserId(userId)?.level ?: properties.lowest.level
            val after = evaluate(userId, now).level
            when {
                after > before -> promoted++
                after < before -> demoted++
            }
        }
        return GradeEvaluationResult(evaluated = userIds.size, promoted = promoted, demoted = demoted)
    }

    private fun collect(userId: Long, now: LocalDateTime): GradeMetrics {
        val ratings = roomRatingQueryService.starsOfHost(userId)
        return GradeMetrics(
            roomsHosted = roomRepository
                .countByHostUserIdAndStatusAndStartedAtIsNotNull(userId, RoomStatus.ENDED).toInt(),
            totalStudents = roomStatsService.getUserRoomStats(userId).totalStudentCount.toInt(),
            avgRating = ratings.overallAverage,
            ratingCount = ratings.totalCount,
            activeInWindow = roomRepository.countByHostUserIdAndStatusAndEndedAtGreaterThanEqual(
                userId,
                RoomStatus.ENDED,
                now.minusDays(properties.maintenanceDays),
            ).toInt(),
        )
    }
}
