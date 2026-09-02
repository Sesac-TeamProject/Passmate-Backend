package kr.passmate.session.service

import kr.passmate.common.event.AnswerScoreAdjustedEvent
import kr.passmate.common.event.SessionEndedEvent
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.question.domain.Question
import kr.passmate.question.domain.QuestionSetStatus
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.repository.RoomRepository
import kr.passmate.session.domain.SessionEventType
import kr.passmate.session.domain.SessionQuestion
import kr.passmate.session.dto.QuestionEndedPayload
import kr.passmate.session.dto.QuestionStartedPayload
import kr.passmate.session.dto.ScreenLockPayload
import kr.passmate.session.repository.RoomStateRepository
import kr.passmate.session.repository.SessionQuestionRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/**
 * 세션 제어. **호스트의 REST 호출로만 상태가 바뀐다** — WebSocket 은 결과를 흘려보내기만 한다.
 * 그래서 호스트 검증이 여기 한 곳에만 있으면 된다.
 */
@Service
class SessionService(
    private val roomRepository: RoomRepository,
    private val sessionQuestionRepository: SessionQuestionRepository,
    private val questionSetQueryService: QuestionSetQueryService,
    private val roomStateRepository: RoomStateRepository,
    private val sessionQueryService: SessionQueryService,
    private val eventPublisher: SessionEventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    /**
     * 세션을 시작한다. 확정된 문제 세트의 문항을 session_question 으로 복사해 두고 1번 문항을 연다.
     * 복사하는 이유: 세트는 여러 방에 재사용되는데 시작·마감 시각과 집계는 방마다 다르다.
     */
    @Transactional
    fun start(roomId: Long, hostUserId: Long) {
        val room = ownedRoom(roomId, hostUserId)
        val setId = room.questionSetId ?: throw BusinessException(ErrorCode.QUESTION_SET_REQUIRED)
        val (set, questions) = questionSetQueryService.getDetail(setId, hostUserId)

        if (set.status != QuestionSetStatus.CONFIRMED) {
            throw BusinessException(ErrorCode.QUESTION_SET_REQUIRED, "확정된 세트만 출제할 수 있습니다.")
        }
        if (questions.isEmpty()) throw BusinessException(ErrorCode.QUESTION_SET_EMPTY)

        room.start()
        sessionQuestionRepository.saveAll(
            questions.map {
                SessionQuestion(
                    roomId = roomId,
                    questionId = it.id,
                    orderNo = it.orderNo,
                    timeLimitSec = it.timeLimitSec,
                )
            },
        )
        eventPublisher.toRoom(roomId, SessionEventType.SESSION_STARTED)
        openQuestion(room, questions.first().orderNo, questions)
    }

    /** 현재 문항이 열려 있으면 먼저 닫고, 다음 문항을 연다. */
    @Transactional
    fun next(roomId: Long, hostUserId: Long) {
        val room = ownedRoom(roomId, hostUserId)
        room.verifyRunning()
        val (_, questions) = questionSet(room, hostUserId)

        currentRunning(roomId)?.let { closeQuestion(it, questions) }

        val nextOrderNo = room.currentQuestionNo + 1
        if (questions.none { it.orderNo == nextOrderNo }) {
            throw BusinessException(ErrorCode.SESSION_ALREADY_FINISHED)
        }
        openQuestion(room, nextOrderNo, questions)
    }

    /** 호스트가 제한시간 전에 바로 마감한다. */
    @Transactional
    fun endCurrentQuestion(roomId: Long, hostUserId: Long) {
        val room = ownedRoom(roomId, hostUserId)
        room.verifyRunning()
        val current = currentRunning(roomId)
            ?: throw BusinessException(ErrorCode.QUESTION_NOT_RUNNING)
        val (_, questions) = questionSet(room, hostUserId)
        closeQuestion(current, questions)
    }

    /** 세션을 끝낸다. 열려 있던 문항도 함께 닫는다. */
    @Transactional
    fun end(roomId: Long, hostUserId: Long) {
        val room = ownedRoom(roomId, hostUserId)
        room.verifyRunning()
        val (_, questions) = questionSet(room, hostUserId)
        currentRunning(roomId)?.let { closeQuestion(it, questions) }

        room.close()
        recordRoomResult(room)
        eventPublisher.toRoom(roomId, SessionEventType.SESSION_ENDED, sessionQueryService.ranking(roomId))

        // 개인 학습 리포트는 report 기능이 만든다. 직접 부르면 session ⇄ report 순환이라 이벤트로 끊는다
        applicationEventPublisher.publishEvent(SessionEndedEvent(roomId))
    }

    /**
     * 학생 화면을 잠그거나 푼다(FR-062). 진행 중일 때만 — 잠금은 "지금 나를 보라"는 신호라
     * 세션이 돌고 있지 않으면 뜻이 없다.
     *
     * 잠금은 서버 상태다. 브로드캐스트는 화면을 덮으라는 알림일 뿐이고,
     * 실제 제출 차단은 AnswerService 가 room.screenLocked 를 보고 막는다 —
     * 클라이언트가 이벤트를 무시해도 답안은 들어가지 않는다.
     */
    @Transactional
    fun lockScreen(roomId: Long, hostUserId: Long, locked: Boolean): Room {
        val room = ownedRoom(roomId, hostUserId)
        room.verifyRunning()
        room.lockScreen(locked)
        eventPublisher.toRoom(roomId, SessionEventType.SCREEN_LOCKED, ScreenLockPayload(locked))
        return room
    }

    /**
     * 제한시간이 지난 문항을 마감한다(서버 권위 타이머가 호출).
     * 호스트의 "바로 마감"과 겹쳐도 SessionQuestion.end() 가 멱등이라 두 번 닫히지 않는다.
     */
    @Transactional
    fun endByTimeout(sessionQuestionId: Long) {
        val sq = sessionQuestionRepository.findById(sessionQuestionId).orElse(null) ?: return
        if (sq.isEnded) return
        val room = roomRepository.findById(sq.roomId).orElse(null) ?: return
        val questions = runCatching { questionSetQueryService.getDetail(room.questionSetId!!, room.hostUserId).second }
            .getOrNull() ?: return
        closeQuestion(sq, questions)
    }

    /**
     * 첨삭으로 점수가 바뀌면 방 요약(평균 점수)을 다시 박아 둔다.
     *
     * `room.avg_score` 는 "내가 만든 방" 목록이 방마다 답안을 다시 세지 않으려고 굳혀 둔 값이다.
     * 굳혀 둔 이상 원본이 바뀔 때 같이 갱신하지 않으면 목록만 옛 평균을 들고 있게 된다.
     * 정답률은 자동 채점 결과라 서술형 보정에 흔들리지 않는다.
     */
    @EventListener
    @Transactional
    fun onAnswerScoreAdjusted(event: AnswerScoreAdjustedEvent) {
        val room = roomRepository.findById(event.roomId).orElse(null) ?: return
        if (room.status != RoomStatus.ENDED) return
        recordRoomResult(room)
    }

    // ---------- 내부 ----------

    private fun openQuestion(room: Room, orderNo: Int, questions: List<Question>) {
        val sq = sessionQuestionRepository.findByRoomIdAndOrderNo(room.id, orderNo)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "출제할 문항이 없습니다.")
        val question = questions.first { it.orderNo == orderNo }

        sq.start()
        room.advanceQuestion(orderNo)

        // 정답·해설은 싣지 않는다 — 마감할 때 처음 나간다
        eventPublisher.toRoom(
            room.id,
            SessionEventType.QUESTION_STARTED,
            QuestionStartedPayload(
                sessionQuestionId = sq.id,
                questionId = question.id,
                orderNo = orderNo,
                totalCount = questions.size,
                type = question.type,
                content = question.content,
                choices = question.choices,
                points = question.points,
                timeLimitSec = sq.timeLimitSec,
                endsAt = requireNotNull(sq.endsAt),
            ),
        )
    }

    private fun closeQuestion(sq: SessionQuestion, questions: List<Question>) {
        val stat = roomStateRepository.findSubmissionStat(sq.id)
        sq.end(stat.submitCount, stat.correctCount, stat.distribution)
        val question = questions.firstOrNull { it.id == sq.questionId }

        eventPublisher.toRoom(
            sq.roomId,
            SessionEventType.QUESTION_ENDED,
            QuestionEndedPayload(
                sessionQuestionId = sq.id,
                questionId = sq.questionId,
                orderNo = sq.orderNo,
                answer = question?.answer,
                explanation = question?.explanation,
                submitCount = sq.submitCount,
                correctCount = sq.correctCount,
                correctRate = sq.correctRate?.toDouble() ?: 0.0,
                distribution = stat.distribution,
            ),
        )
        eventPublisher.toRoom(sq.roomId, SessionEventType.RANKING_UPDATED, sessionQueryService.ranking(sq.roomId))
    }

    /**
     * 방의 평균 점수·정답률을 한 번 계산해 박아 둔다.
     * 마이페이지 "내가 만든 방" 목록이 방마다 답안을 다시 세지 않게 하려는 값이다.
     */
    private fun recordRoomResult(room: Room) {
        val questions = sessionQuestionRepository.findAllByRoomIdOrderByOrderNoAsc(room.id)
        val submitCount = questions.sumOf { it.submitCount }
        val correctCount = questions.sumOf { it.correctCount }
        val totalScore = roomStateRepository.findRanking(room.id).sumOf { it.totalScore }

        room.recordResult(
            avgScore = if (room.participantCount == 0) BigDecimal.ZERO
            else BigDecimal(totalScore.toDouble() / room.participantCount).setScale(2, RoundingMode.HALF_UP),
            correctRate = if (submitCount == 0) BigDecimal.ZERO
            else BigDecimal(correctCount * 100.0 / submitCount).setScale(2, RoundingMode.HALF_UP),
        )
    }

    private fun currentRunning(roomId: Long): SessionQuestion? =
        sessionQuestionRepository.findAllByRoomIdOrderByOrderNoAsc(roomId).firstOrNull { it.isRunning }

    private fun questionSet(room: Room, hostUserId: Long): Pair<Long, List<Question>> {
        val setId = room.questionSetId ?: throw BusinessException(ErrorCode.QUESTION_SET_REQUIRED)
        return setId to questionSetQueryService.getDetail(setId, hostUserId).second
    }

    private fun ownedRoom(roomId: Long, hostUserId: Long): Room {
        val room = roomRepository.findById(roomId)
            .orElseThrow { BusinessException(ErrorCode.ROOM_NOT_FOUND) }
        room.verifyHost(hostUserId)
        return room
    }
}
