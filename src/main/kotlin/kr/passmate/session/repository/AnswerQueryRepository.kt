package kr.passmate.session.repository

import kr.passmate.session.domain.Answer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnswerQueryRepository : JpaRepository<Answer, Long> {

    /**
     * 방 전체 누적 점수. session_question 을 통해 방을 좁힌다.
     * 보정된 final_score 를 쓴다 — 서술형은 AI 분석 뒤에 값이 바뀐다.
     */
    @Query(
        """
        select new kr.passmate.session.repository.ParticipantScore(a.participantId, sum(a.finalScore))
        from Answer a
        join SessionQuestion sq on sq.id = a.sessionQuestionId
        where sq.roomId = :roomId
        group by a.participantId
        """,
    )
    fun sumScoreByParticipant(@Param("roomId") roomId: Long): List<ParticipantScore>

    /**
     * [upToOrderNo] 번 문항까지의 누적 점수. 순위 변동은 "직전 문항 마감 시점"과 견줘야 하는데,
     * 그 시점의 랭킹을 따로 저장해 두지 않아 답안에서 다시 센다(웹 QA_BACKLOG B-17).
     */
    @Query(
        """
        select new kr.passmate.session.repository.ParticipantScore(a.participantId, sum(a.finalScore))
        from Answer a
        join SessionQuestion sq on sq.id = a.sessionQuestionId
        where sq.roomId = :roomId and sq.orderNo <= :upToOrderNo
        group by a.participantId
        """,
    )
    fun sumScoreByParticipantUpTo(
        @Param("roomId") roomId: Long,
        @Param("upToOrderNo") upToOrderNo: Int,
    ): List<ParticipantScore>

    /**
     * 방에서 나온 답안 전부. 결과 화면이 문항 × 참가자 격자를 그리는 재료다.
     * 참가자마다 따로 조회하면 참가자 수만큼 쿼리가 나간다.
     */
    @Query(
        """
        select a from Answer a
        join SessionQuestion sq on sq.id = a.sessionQuestionId
        where sq.roomId = :roomId
        """,
    )
    fun findAllByRoomId(@Param("roomId") roomId: Long): List<Answer>
}
