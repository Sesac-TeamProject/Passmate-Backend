package kr.passmate.session.repository

import kr.passmate.session.domain.Answer
import org.springframework.stereotype.Repository

/**
 * MySQL 집계로 구현한 세션 상태 조회.
 *
 * 방 하나의 참가자·답안은 많아야 수백 건이라 답안을 읽어 메모리에서 집계한다.
 * SQL 로 밀어넣으면 응답 분포 같은 건 쿼리가 복잡해지는데 얻는 게 없다.
 */
@Repository
class JpaRoomStateRepository(
    private val answerQueryRepository: AnswerQueryRepository,
    private val answerRepository: AnswerRepository,
) : RoomStateRepository {

    override fun findRanking(roomId: Long): List<ParticipantScore> =
        answerQueryRepository.sumScoreByParticipant(roomId)
            .sortedWith(compareByDescending<ParticipantScore> { it.totalScore }.thenBy { it.participantId })

    override fun findSubmissionStat(sessionQuestionId: Long): SubmissionStat {
        val answers: List<Answer> = answerRepository.findAllBySessionQuestionId(sessionQuestionId)
        return SubmissionStat(
            submitCount = answers.size,
            correctCount = answers.count { it.isCorrect == true },
            // 서술형은 자유 텍스트라 분포가 의미 없다. 채점된(정오가 있는) 문항만 센다
            distribution = answers
                .filter { it.isCorrect != null }
                .groupingBy { it.submitted }
                .eachCount(),
        )
    }
}
