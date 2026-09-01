package kr.passmate.session.repository

/** 참가자별 누적 점수. 닉네임은 여기서 채우지 않는다 — 참가자는 room 기능 소유다. */
data class ParticipantScore(
    val participantId: Long,
    val totalScore: Long,
)

/** 한 문항의 제출 집계. */
data class SubmissionStat(
    val submitCount: Int,
    val correctCount: Int,
    /** 보기별 응답 수. 서술형은 비어 있다 */
    val distribution: Map<String, Int>,
)

/**
 * 세션 진행 상태·랭킹 조회 창구.
 *
 * **서비스는 이 인터페이스만 안다.** 지금 구현체는 MySQL 집계(`JpaRoomStateRepository`)지만,
 * 나중에 Redis 를 도입하면 구현체를 하나 더 만들어 갈아끼우는 것으로 끝난다.
 * 서비스가 구현체를 직접 알면 그 전환이 서비스 수정으로 번진다.
 */
interface RoomStateRepository {

    /** 누적 점수 내림차순. 동점이면 participantId 오름차순으로 안정 정렬한다. */
    fun findRanking(roomId: Long): List<ParticipantScore>

    fun findSubmissionStat(sessionQuestionId: Long): SubmissionStat
}
