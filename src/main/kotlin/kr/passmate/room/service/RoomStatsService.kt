package kr.passmate.room.service

import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.repository.ParticipantRepository
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 마이페이지 요약 지표(C-02 v3 · M-12). */
data class UserRoomStats(
    /** 참여한 방 수 */
    val joinedRoomCount: Long,
    /** 내가 만든 방 수 */
    val hostedRoomCount: Long,
    /** 진행한 세션 수 — 시작해서 종료까지 간 방만 센다(등급 판정과 같은 기준, FR-045) */
    val hostedSessionCount: Long,
    /** 누적 학생 수 — 종료된 내 방들의 참가자 합 */
    val totalStudentCount: Long,
)

/**
 * 방 관련 집계만 담당한다.
 *
 * `RoomQueryService` 와 나누어 둔 이유는 **순환 참조 때문**이다 —
 * 목록 조회는 호스트 닉네임이 필요해 user 를 부르고, 마이페이지는 이 집계가 필요해 room 을 부른다.
 * 한 빈에 몰아넣으면 user ⇄ room 이 되어 생성자 주입이 실패한다.
 */
@Service
@Transactional(readOnly = true)
class RoomStatsService(
    private val roomRepository: RoomRepository,
    private val participantRepository: ParticipantRepository,
) {

    fun getUserRoomStats(userId: Long) = UserRoomStats(
        joinedRoomCount = participantRepository.countByUserId(userId),
        hostedRoomCount = roomRepository.countByHostUserId(userId),
        hostedSessionCount = roomRepository.countByHostUserIdAndStatus(userId, RoomStatus.ENDED),
        totalStudentCount = roomRepository.sumParticipantCountByHostAndStatus(userId, RoomStatus.ENDED),
    )
}
