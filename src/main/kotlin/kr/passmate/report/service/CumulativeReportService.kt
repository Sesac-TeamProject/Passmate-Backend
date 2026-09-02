package kr.passmate.report.service

import kr.passmate.report.domain.ParticipantReport
import kr.passmate.report.dto.CumulativeReportResponse
import kr.passmate.report.dto.SessionTrendPoint
import kr.passmate.report.repository.ParticipantReportRepository
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 누적 학습 리포트 (FR-033).
 *
 * 세션이 끝날 때 찍어 둔 개인 리포트를 모아 평균을 낸다 — 답안을 다시 훑지 않는다.
 * 그래서 나중에 문항이 고쳐져도 지난 성적이 흔들리지 않는다.
 */
@Service
@Transactional(readOnly = true)
class CumulativeReportService(
    private val participantQueryService: ParticipantQueryService,
    private val roomQueryService: RoomQueryService,
    private val reportRepository: ParticipantReportRepository,
) {

    fun getCumulativeReport(userId: Long): CumulativeReportResponse {
        val participants = participantQueryService.listByUser(userId)
        val roomIdByParticipant = participants.associate { it.id to it.roomId }

        val reports = reportRepository
            .findAllByParticipantIdIn(participants.map { it.id })
            .sortedByDescending { it.generatedAt }

        if (reports.isEmpty()) {
            return CumulativeReportResponse(
                joinedRoomCount = participants.size,
                completedSessionCount = 0,
                averageAccuracy = 0.0,
                averageRank = 0.0,
                accuracyChangeFromLastWeek = null,
                trend = emptyList(),
                weakTopics = emptyList(),
            )
        }

        val rooms = roomQueryService.getRooms(reports.mapNotNull { roomIdByParticipant[it.participantId] })

        return CumulativeReportResponse(
            joinedRoomCount = participants.size,
            completedSessionCount = reports.size,
            averageAccuracy = round2(reports.map { it.accuracy.toDouble() }.average()),
            averageRank = round2(reports.map { it.finalRank.toDouble() }.average()),
            accuracyChangeFromLastWeek = accuracyChange(reports),
            trend = reports.take(TREND_SIZE).mapNotNull { report ->
                val room = roomIdByParticipant[report.participantId]?.let { rooms[it] } ?: return@mapNotNull null
                SessionTrendPoint(
                    roomId = room.id,
                    roomTitle = room.title,
                    totalScore = report.totalScore,
                    accuracy = report.accuracy.toDouble(),
                    finalRank = report.finalRank,
                    playedAt = report.generatedAt,
                )
            },
            weakTopics = weakTopics(reports),
        )
    }

    /**
     * 최근 7일 평균 정답률에서 그 앞 7일 평균을 뺀 값.
     * **한쪽이라도 기록이 없으면 null** 이다 — 0.0 으로 주면 "변화 없음"으로 읽힌다.
     */
    private fun accuracyChange(reports: List<ParticipantReport>): Double? {
        val now = LocalDateTime.now()
        val lastWeek = now.minusDays(WEEK)
        val weekBefore = now.minusDays(WEEK * 2)

        val recent = reports.filter { it.generatedAt >= lastWeek }
        val previous = reports.filter { it.generatedAt >= weekBefore && it.generatedAt < lastWeek }
        if (recent.isEmpty() || previous.isEmpty()) return null

        return round2(
            recent.map { it.accuracy.toDouble() }.average() - previous.map { it.accuracy.toDouble() }.average(),
        )
    }

    /** 여러 세션에서 반복해 나온 주제일수록 앞에 둔다 — 한 번 틀린 주제는 약점이라 하기 어렵다. */
    private fun weakTopics(reports: List<ParticipantReport>): List<String> =
        reports.flatMap { it.weakTopics.orEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(WEAK_TOPIC_SIZE)
            .map { it.key }

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    private companion object {
        const val WEEK = 7L
        const val TREND_SIZE = 10
        const val WEAK_TOPIC_SIZE = 5
    }
}
