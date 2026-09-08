package kr.passmate.room.service

import kr.passmate.hostlevel.service.HostGradeQueryService
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
    private val hostGradeQueryService: HostGradeQueryService,
) {

    fun getHostedRooms(hostUserId: Long): HostedRoomsResponse {
        val rooms = roomRepository.findAllByHostUserIdOrderByIdDesc(hostUserId)
        // 종료 여부가 아니라 isActive 로 가른다 — 취소된 방이 진행 중에 섞이면
        // "진행 중인 방 열기"가 취소된 방을 연다. 취소는 종료 목록에 status 로 담는다(W-09·M-13 결정)
        val (active, finished) = rooms.partition { it.status.isActive }

        val ratings = roomRatingQueryService.starsOfHost(hostUserId)
        // 세션을 한 번도 안 한 회원은 프로필이 없다 — 그때는 등급 자리를 비워 둔다
        val grade = hostGradeQueryService.findProfile(hostUserId)
            ?.let { hostGradeQueryService.toResponse(it) }
        val stats = roomStatsService.getUserRoomStats(hostUserId)
        // 정상 종료된 방만 학생 수를 센다 — 진행 중인 방은 room.participantCount 가 곧 현재 인원이고,
        // 취소된 방은 대기실까지 들어온 사람이 있어도 "학생 1명"으로 읽히면 안 된다(세션이 없었다)
        val studentCounts = participantRepository
            .countByRoomIds(finished.filter { it.status == RoomStatus.ENDED }.map { it.id })
            .associate { it.roomId to it.count }

        return HostedRoomsResponse(
            reputation = HostReputation(
                // 아직 판정된 적 없으면 null. 0 으로 주면 "Lv.0" 으로 읽힌다
                level = grade?.level,
                nextLevelProgress = grade?.nextLevelProgress,
                hostedSessionCount = stats.hostedSessionCount,
                totalStudentCount = stats.totalStudentCount,
                averageStars = ratings.overallAverage,
                ratingCount = ratings.totalCount,
            ),
            active = active.map { it.toActive() },
            ended = finished.map { room ->
                EndedHostedRoom(
                    roomId = room.id,
                    title = room.title,
                    status = room.status,
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
