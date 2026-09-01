package kr.passmate.report.service

import kr.passmate.common.dto.PageResponse
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.report.dto.JoinedRoom
import kr.passmate.report.dto.JoinedRoomsResponse
import kr.passmate.report.dto.JoinedSummary
import kr.passmate.report.repository.ParticipantReportRepository
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.user.service.UserQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.ceil

/**
 * 참여한 방 목록 (FR-032 · FR-033).
 *
 * 요약이 전체 기록을 봐야 해서 참가 기록은 어차피 한 번에 읽는다 —
 * 그래서 페이지도 그 목록에서 잘라 낸다. 방·호스트 닉네임·문항 수는
 * **페이지에 걸린 것만** 묶어서 조회한다(N+1 회피).
 *
 * report 에 두는 이유는 이 목록의 알맹이가 점수·순위·리포트 여부이기 때문이다.
 * room 에 두면 room → report 가 되어 이미 있는 report → room 과 순환이 된다.
 */
@Service
@Transactional(readOnly = true)
class JoinedRoomQueryService(
    private val participantQueryService: ParticipantQueryService,
    private val roomQueryService: RoomQueryService,
    private val userQueryService: UserQueryService,
    private val questionSetQueryService: QuestionSetQueryService,
    private val reportRepository: ParticipantReportRepository,
    private val cumulativeReportService: CumulativeReportService,
) {

    fun getJoinedRooms(userId: Long, page: Int, size: Int): JoinedRoomsResponse {
        val participants = participantQueryService.listByUser(userId)
        val reports = reportRepository
            .findAllByParticipantIdIn(participants.map { it.id })
            .associateBy { it.participantId }

        val pageIndex = page.coerceAtLeast(0)
        val pageSize = size.coerceIn(MIN_SIZE, MAX_SIZE)
        val window = participants.drop(pageIndex * pageSize).take(pageSize)

        val rooms = roomQueryService.getRooms(window.map { it.roomId })
        val hostNicknames = userQueryService.getNicknames(rooms.values.map { it.hostUserId })
        val questionCounts = questionSetQueryService.getQuestionCounts(rooms.values.mapNotNull { it.questionSetId })

        val content = window.mapNotNull { participant ->
            val room = rooms[participant.roomId] ?: return@mapNotNull null
            val report = reports[participant.id]
            JoinedRoom(
                roomId = room.id,
                title = room.title,
                hostNickname = hostNicknames[room.hostUserId] ?: UNKNOWN_HOST,
                status = room.status,
                startedAt = room.startedAt,
                endedAt = room.endedAt,
                questionCount = room.questionSetId?.let { questionCounts[it] } ?: 0,
                fee = room.fee,
                myScore = report?.totalScore,
                myRank = report?.finalRank,
                myAccuracy = report?.accuracy?.toDouble(),
                // 리포트는 세션이 끝날 때 찍힌다 — 진행 중인 방에는 아직 없다
                hasReport = report != null && room.status == RoomStatus.ENDED,
            )
        }

        val cumulative = cumulativeReportService.getCumulativeReport(userId)
        return JoinedRoomsResponse(
            summary = JoinedSummary(
                completedSessionCount = cumulative.completedSessionCount,
                averageAccuracy = cumulative.averageAccuracy,
                averageRank = cumulative.averageRank,
                weakTopics = cumulative.weakTopics,
            ),
            rooms = PageResponse(
                content = content,
                page = pageIndex,
                size = pageSize,
                totalElements = participants.size.toLong(),
                totalPages = ceil(participants.size.toDouble() / pageSize).toInt(),
                hasNext = (pageIndex + 1) * pageSize < participants.size,
            ),
        )
    }

    private companion object {
        const val MIN_SIZE = 1
        const val MAX_SIZE = 50
        const val UNKNOWN_HOST = "알 수 없음"
    }
}
