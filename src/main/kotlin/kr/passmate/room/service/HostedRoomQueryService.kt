package kr.passmate.room.service

import kr.passmate.rating.service.RoomRatingQueryService
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.dto.ActiveHostedRoom
import kr.passmate.room.dto.EndedHostedRoom
import kr.passmate.room.dto.HostReputation
import kr.passmate.room.dto.HostedRoomsResponse
import kr.passmate.room.repository.ParticipantRepository
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 내가 만든 방 목록 (FR-038, W-09 · M-13).
 *
 * 진행 중(대기·진행)과 종료를 나눠 준다 — 화면이 두 섹션으로 갈라져 있고
 * 필요한 값도 다르다(진행 중은 PIN·인원, 종료는 성적·별점).
 */
@Service
@Transactional(readOnly = true)
class HostedRoomQueryService(
    private val roomRepository: RoomRepository,
    private val participantRepository: ParticipantRepository,
    private val roomStatsService: RoomStatsService,
    private val roomRatingQueryService: RoomRatingQueryService,
) {

    fun getHostedRooms(hostUserId: Long): HostedRoomsResponse {
        val rooms = roomRepository.findAllByHostUserIdOrderByIdDesc(hostUserId)
        val (ended, active) = rooms.partition { it.status == RoomStatus.ENDED }

        val ratings = roomRatingQueryService.starsOfHost(hostUserId)
        val stats = roomStatsService.getUserRoomStats(hostUserId)
        // 종료된 방만 학생 수를 세면 된다 — 진행 중인 방은 room.participantCount 가 곧 현재 인원이다
        val studentCounts = participantRepository
            .countByRoomIds(ended.map { it.id })
            .associate { it.roomId to it.count }

        return HostedRoomsResponse(
            reputation = HostReputation(
                // hostlevel 기능 전까지 null. 0 으로 주면 "Lv.0" 으로 읽힌다
                level = null,
                nextLevelProgress = null,
                hostedSessionCount = stats.hostedSessionCount,
                totalStudentCount = stats.totalStudentCount,
                averageStars = ratings.overallAverage,
                ratingCount = ratings.totalCount,
            ),
            active = active.map { it.toActive() },
            ended = ended.map { room ->
                EndedHostedRoom(
                    roomId = room.id,
                    title = room.title,
                    endedAt = room.endedAt,
                    studentCount = studentCounts[room.id] ?: 0L,
                    correctRate = room.correctRate?.toDouble(),
                    averageStars = ratings.averageByRoom[room.id],
                    ratingCount = ratings.countByRoom[room.id] ?: 0,
                )
            },
        )
    }

    private fun Room.toActive() = ActiveHostedRoom(
        roomId = id,
        title = title,
        pin = pin,
        status = status,
        scheduledAt = scheduledAt,
        startedAt = startedAt,
        participantCount = participantCount,
        currentQuestionNo = currentQuestionNo,
    )
}
