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
}
