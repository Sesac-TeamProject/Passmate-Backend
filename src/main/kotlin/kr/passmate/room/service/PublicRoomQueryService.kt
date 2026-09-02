package kr.passmate.room.service

import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.PublicRoomResponse
import kr.passmate.room.dto.PublicRoomSearchRequest
import kr.passmate.room.dto.PublicRoomSort
import kr.passmate.room.dto.PublicRoomStatusFilter
import kr.passmate.room.dto.PublicRoomTypeFilter
import kr.passmate.room.repository.RoomRepository
import kr.passmate.user.service.UserQueryService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 홈 인기 방·탐색 목록(FR-054). **게스트도 볼 수 있다.**
 *
 * 아직 못 넣은 것 두 가지 — 둘 다 해당 기능이 없어서다:
 * - Lv.4 이상 호스트 방 상단 노출 → hostlevel
 * - 차단한 호스트 방 제외 → moderation(user_block)
 * 붙일 때 이 클래스의 정렬·조건에 더한다.
 */
@Service
@Transactional(readOnly = true)
class PublicRoomQueryService(
    private val roomRepository: RoomRepository,
    private val userQueryService: UserQueryService,
    private val questionSetQueryService: QuestionSetQueryService,
) {

    fun search(request: PublicRoomSearchRequest): Page<PublicRoomResponse> {
        val pageable = request.toPageable()
        val keyword = request.q?.trim()?.takeIf { it.isNotEmpty() }

        // "선생님 이름"으로도 찾을 수 있어야 해서 닉네임 → 호스트 id 로 먼저 바꾼다.
        // 빈 목록을 그대로 넘기면 JPQL 의 in () 이 문법 오류라 절대 맞지 않는 값을 하나 넣는다
        val hostIds = keyword
            ?.let { userQueryService.findIdsByNicknameContaining(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(NO_MATCH_ID)

        val (from, to) = todayRange(request.today)
        val page = when (request.sort) {
            PublicRoomSort.POPULAR -> roomRepository.findPublicByPopularity(
                statuses(request.status), roomType(request.type), keyword, hostIds, from, to, pageable,
            )

            PublicRoomSort.UPCOMING -> roomRepository.findPublicByUpcoming(
                statuses(request.status), roomType(request.type), keyword, hostIds, from, to, pageable,
            )
        }
        return page.map(toResponse(page.content))
    }

    /**
     * 카드에 필요한 호스트 닉네임·문항 수를 **한 번씩만** 조회해 붙인다.
     * 방마다 따로 부르면 페이지 크기만큼 쿼리가 늘어난다(N+1).
     */
    private fun toResponse(rooms: List<Room>): (Room) -> PublicRoomResponse {
        val nicknames = userQueryService.getNicknames(rooms.map { it.hostUserId })
        val questionCounts = questionSetQueryService.getQuestionCounts(rooms.mapNotNull { it.questionSetId })

        return { room ->
            PublicRoomResponse.of(
                room = room,
                hostNickname = nicknames[room.hostUserId],
                questionCount = room.questionSetId?.let { questionCounts[it] },
            )
        }
    }

    /** 종료·취소된 방은 어느 필터로도 나오지 않는다. */
    private fun statuses(filter: PublicRoomStatusFilter?): List<RoomStatus> = when (filter) {
        PublicRoomStatusFilter.WAITING -> listOf(RoomStatus.WAITING)
        PublicRoomStatusFilter.RUNNING -> listOf(RoomStatus.RUNNING)
        null -> OPEN_STATUSES
    }

    private fun roomType(filter: PublicRoomTypeFilter?): RoomType? = when (filter) {
        PublicRoomTypeFilter.FREE -> RoomType.FREE
        PublicRoomTypeFilter.PAID -> RoomType.PAID
        // 비우면 브랜디드 방도 함께 나온다 — 홈 인기 방에 같이 노출되는 게 명세다
        null -> null
    }

    /** 오늘 필터는 서버 시각 기준 [오늘 00:00, 내일 00:00) 이다. */
    private fun todayRange(today: Boolean): Pair<LocalDateTime?, LocalDateTime?> =
        if (!today) null to null
        else LocalDate.now().atStartOfDay() to LocalDate.now().plusDays(1).atStartOfDay()

    private companion object {
        val OPEN_STATUSES = listOf(RoomStatus.WAITING, RoomStatus.RUNNING)

        /** 존재할 수 없는 id. 검색어에 걸린 호스트가 없을 때 in 절을 비우지 않기 위한 값 */
        const val NO_MATCH_ID = -1L
    }
}
