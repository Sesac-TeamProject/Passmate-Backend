package kr.passmate.report.service

import kr.passmate.question.domain.Question
import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.Room
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.session.domain.Answer
import kr.passmate.session.domain.SessionQuestion
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.session.service.SessionQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 방 하나 분량의 결과 재료. 결과 조회·리포트 생성·CSV 내보내기가 모두 이 한 벌을 쓴다.
 *
 * 점수·순위는 답안에서 바로 계산한다 — 이미 손에 들고 있는 값이라 랭킹을 따로 조회할
 * 이유가 없고, 중도 이탈자까지 같은 기준으로 줄을 세울 수 있다.
 */
class SessionMaterials(
    val room: Room,
    val sessionQuestions: List<SessionQuestion>,
    val questionsById: Map<Long, Question>,
    val participants: List<Participant>,
    val answers: List<Answer>,
) {

    val answersBySessionQuestion: Map<Long, List<Answer>> = answers.groupBy { it.sessionQuestionId }

    private val answersByParticipant: Map<Long, List<Answer>> = answers.groupBy { it.participantId }

    /** 보정된 final_score 로 센다 — 서술형은 첨삭 뒤에 값이 바뀐다. */
    private val scores: Map<Long, Long> =
        answersByParticipant.mapValues { (_, list) -> list.sumOf { it.finalScore }.toLong() }

    /** 동점은 같은 등수로 묶는다(공동 3등 다음은 5등). 답안이 없는 사람은 0점 공동 꼴등. */
    private val ranks: Map<Long, Int> = participants
        .map { it.id to (scores[it.id] ?: 0L) }
        .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenBy { it.first })
        .let { rows ->
            var rank = 0
            var prev: Long? = null
            rows.mapIndexed { index, (participantId, score) ->
                if (score != prev) {
                    rank = index + 1
                    prev = score
                }
                participantId to rank
            }
        }
        .toMap()

    fun scoreOf(participantId: Long): Long = scores[participantId] ?: 0L

    fun rankOf(participantId: Long): Int = ranks[participantId] ?: 0

    fun answersOf(participantId: Long): List<Answer> = answersByParticipant[participantId].orEmpty()

    fun correctCountOf(participantId: Long): Int = answersOf(participantId).count { it.isCorrect == true }

    /**
     * 이 참가자가 못 맞힌 문항의 주제. **채점 전인 서술형은 빼고** 오답·미제출만 센다 —
     * 아직 채점되지 않은 문항을 약점으로 적으면 없는 약점을 만들어 낸다.
     */
    fun weakTopicsOf(participantId: Long): List<String> {
        val mine = answersOf(participantId).associateBy { it.sessionQuestionId }
        return sessionQuestions
            .filter { sq ->
                val answer = mine[sq.id]
                answer == null || answer.isCorrect == false
            }
            .mapNotNull { questionsById[it.questionId]?.topic?.takeIf(String::isNotBlank) }
            .distinct()
    }
}

/** 재료를 한 번에 읽어 오는 자리. 세 화면이 각자 읽으면 같은 쿼리가 세 벌 나간다. */
@Service
@Transactional(readOnly = true)
class SessionMaterialsLoader(
    private val sessionQueryService: SessionQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val answerQueryService: AnswerQueryService,
) {

    fun load(room: Room): SessionMaterials = SessionMaterials(
        room = room,
        sessionQuestions = sessionQueryService.sessionQuestions(room.id),
        questionsById = sessionQueryService.questionsOf(room).associateBy { it.id },
        participants = participantQueryService.listAll(room.id),
        answers = answerQueryService.listByRoom(room.id),
    )
}
