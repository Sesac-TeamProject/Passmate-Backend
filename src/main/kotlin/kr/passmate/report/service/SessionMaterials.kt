package kr.passmate.report.service

import kr.passmate.question.domain.Question
import kr.passmate.report.dto.TopicAccuracy
import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.Room
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.session.domain.Answer
import kr.passmate.session.domain.SessionQuestion
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.session.service.SessionQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

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

    private val sessionQuestionsById: Map<Long, SessionQuestion> = sessionQuestions.associateBy { it.id }

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

    /** 문항 수 기준 정답률(%). 미제출도 오답으로 센다 — participant_report.accuracy 와 같은 정의 */
    fun accuracyOf(participantId: Long): Double = percent(correctCountOf(participantId), sessionQuestions.size)

    /** "반 평균과 비교" 카드의 반 평균. 한 문제도 안 푼 사람도 0% 로 평균에 든다 */
    val classAvgAccuracy: Double
        get() = if (participants.isEmpty()) 0.0 else participants.map { accuracyOf(it.id) }.average()

    /** "반 평균과 비교" 카드의 1위 정답률 */
    val topAccuracy: Double
        get() = participants.maxOfOrNull { accuracyOf(it.id) } ?: 0.0

    /** 문항의 반 정답률(%). 채점된(정오가 있는) 답안이 분모 — 방 리포트 문항별 탭과 같은 정의 */
    fun correctRateOf(sessionQuestionId: Long): Double {
        val graded = answersBySessionQuestion[sessionQuestionId].orEmpty().filter { it.isCorrect != null }
        return percent(graded.count { it.isCorrect == true }, graded.size)
    }

    /** 문항이 열린 뒤 제출까지 걸린 시간(ms). 서버 시각끼리의 차라 클라이언트 시계와 무관하다 */
    fun elapsedMsOf(answer: Answer): Long? {
        val startedAt = sessionQuestionsById[answer.sessionQuestionId]?.startedAt ?: return null
        return Duration.between(startedAt, answer.submittedAt).toMillis().coerceAtLeast(0)
    }

    /** 제출한 문항의 소요 시간 합. 아무것도 안 냈으면 null — 0ms 와 "안 풀었다"는 다르다 */
    fun elapsedMsOf(participantId: Long): Long? =
        answersOf(participantId).mapNotNull { elapsedMsOf(it) }.takeIf { it.isNotEmpty() }?.sum()

    /**
     * 주제별 맞은 수/전체 수 — "개념별 정답률" 카드. 미제출은 오답이다.
     * 주제가 비어 있는 문항은 어느 주제에도 넣지 않는다. 순서는 문항 순서대로 처음 나온 주제 순.
     */
    fun topicAccuracyOf(participantId: Long): List<TopicAccuracy> {
        val mine = answersOf(participantId).associateBy { it.sessionQuestionId }
        return sessionQuestions
            .mapNotNull { sq ->
                val topic = questionsById[sq.questionId]?.topic?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                topic to (mine[sq.id]?.isCorrect == true)
            }
            .groupBy({ it.first }, { it.second })
            .map { (topic, results) ->
                val correct = results.count { it }
                TopicAccuracy(topic, correct, results.size, percent(correct, results.size))
            }
    }

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

    companion object {
        /** 백분율. 분모가 0 이면 0 — 아무도 안 풀었을 때 NaN 을 내보내지 않는다 */
        fun percent(part: Int, whole: Int): Double =
            if (whole == 0) 0.0 else part * 100.0 / whole
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
