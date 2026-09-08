package kr.passmate.session

import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.service.RoomService
import kr.passmate.room.dto.QuestionTimeEntry
import kr.passmate.room.dto.RoomQuestionTimesRequest
import kr.passmate.session.repository.SessionQuestionRepository
import kr.passmate.session.service.QuestionTimeoutScheduler
import kr.passmate.session.service.SessionService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime

/**
 * 서버 권위 타이머가 마감을 **DB 에 남기는지** 본다 (웹 QA_BACKLOG B-12).
 *
 * ⚠️ **이 클래스에는 `@Transactional` 을 붙이지 않는다.** 테스트가 트랜잭션을 열어 두면
 * 스케줄러의 `readOnly` 트랜잭션이 그 쓰기 트랜잭션에 합류해 버려(합류 시 readOnly 는 무시된다)
 * 정작 문제가 되는 상황이 재현되지 않는다 — 이 버그가 통합 테스트를 통과해 온 이유다.
 * 대신 실제로 커밋되므로 데이터는 남는다(다른 실시간 테스트와 같은 방식).
 */
class QuestionTimeoutSchedulerTest : IntegrationTestSupport() {

    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var sessionService: SessionService
    @Autowired private lateinit var scheduler: QuestionTimeoutScheduler
    @Autowired private lateinit var sessionQuestionRepository: SessionQuestionRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `자동 넘김은 기본 켬 — 마감 뒤 결과 표시 시간이 지나면 다음 문항이 자동으로 열린다`() {
        // 호스트가 타이밍 화면을 안 건드려도 자동 넘김이 돈다(2026-09-08 기본값 반전)
        val roomId = runningRoom(questionCount = 2)
        val (first, second) = sessionQuestionRepository.findAllByRoomIdOrderByOrderNoAsc(roomId)

        expire(first.id)
        scheduler.closeExpiredQuestions()

        // 마감 직후에는 열지 않는다 — 결과 화면(W-06)을 볼 시간을 준다
        assertThat(sessionQuestionRepository.findById(second.id).orElseThrow().startedAt).isNull()

        // 결과 표시 시간이 지난 것으로 만든다
        jdbcTemplate.update(
            "update session_question set ended_at = ? where id = ?",
            LocalDateTime.now().minusSeconds(60), first.id,
        )
        scheduler.closeExpiredQuestions()

        assertThat(sessionQuestionRepository.findById(second.id).orElseThrow().startedAt).isNotNull()

        // 마지막 문항 — 더 열 문항이 없으면 세션을 자동 종료한다(시나리오 테스트 "세션 종료 명확화")
        expire(second.id)
        scheduler.closeExpiredQuestions()
        jdbcTemplate.update(
            "update session_question set ended_at = ? where id = ?",
            LocalDateTime.now().minusSeconds(60), second.id,
        )
        scheduler.closeExpiredQuestions()

        val status = jdbcTemplate.queryForObject("select status from room where id = ?", String::class.java, roomId)
        assertThat(status).isEqualTo("ENDED")
        // 끝난 방은 다음 틱의 대상에서도 빠진다 — 폴링이 지난 세션을 계속 집지 않는다
        assertThat(sessionQuestionRepository.findAutoAdvanceDue(LocalDateTime.now())).isEmpty()
    }

    @Test
    fun `자동 넘김을 끈 문항은 마감돼도 다음 문항이 열리지 않는다`() {
        val roomId = runningRoom(questionCount = 2, autoAdvanceOffAll = true)
        val (first, second) = sessionQuestionRepository.findAllByRoomIdOrderByOrderNoAsc(roomId)

        expire(first.id)
        scheduler.closeExpiredQuestions()
        jdbcTemplate.update(
            "update session_question set ended_at = ? where id = ?",
            LocalDateTime.now().minusSeconds(60), first.id,
        )
        scheduler.closeExpiredQuestions()

        // 호스트가 "다음 문항"을 누를 때까지 기다린다 — 지금까지의 진행 방식 그대로
        assertThat(sessionQuestionRepository.findById(second.id).orElseThrow().startedAt).isNull()
    }

    @Test
    fun `제한시간이 지난 문항은 한 번 마감되고 다음 틱에 다시 잡히지 않는다`() {
        val roomId = runningRoom()
        val sq = sessionQuestionRepository.findAllByRoomIdOrderByOrderNoAsc(roomId).first()
        expire(sq.id)

        scheduler.closeExpiredQuestions()

        // 마감이 실제로 커밋됐는지 — 새로 읽어 본다.
        // readOnly 트랜잭션 안에서 돌면 flush 가 되지 않아 여기서 null 로 남는다
        assertThat(sessionQuestionRepository.findById(sq.id).orElseThrow().endedAt).isNotNull()

        // 그래서 다음 1초 틱의 대상에서도 빠진다 — 20초짜리 문항 하나에 QUESTION_ENDED 가
        // 1,500건 넘게 나가던 원인이 이 자리였다
        val stillExpired = sessionQuestionRepository
            .findAllByEndedAtIsNullAndEndsAtLessThan(LocalDateTime.now())
            .map { it.id }
        assertThat(stillExpired).doesNotContain(sq.id)
    }

    /** 제한시간이 지난 상태로 만든다. 최소 제한시간이 5초라 실제로 기다리지 않는다. */
    private fun expire(sessionQuestionId: Long) {
        jdbcTemplate.update(
            "update session_question set ends_at = ? where id = ?",
            LocalDateTime.now().minusSeconds(1),
            sessionQuestionId,
        )
    }

    /** OX 문항 [questionCount]개짜리 방을 만들고 세션을 시작한다. 자동 넘김은 기본 켬 상태다. */
    private fun runningRoom(questionCount: Int = 1, autoAdvanceOffAll: Boolean = false): Long {
        val key = "timeout-${System.nanoTime()}"
        val hostId = userService.loginOrRegister(AuthProvider.GOOGLE, key, null, "호스트", null).user.id

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("타이머 테스트"))
        val questionIds = (1..questionCount).map { no ->
            questionSetService.addQuestion(
                set.id, hostId,
                QuestionRequest(QuestionType.OX, "문항 $no", answer = "O", timeLimitSec = 5, points = 100),
            ).id
        }
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "타이머", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "타이머", questionSetId = set.id))
        if (autoAdvanceOffAll) {
            roomService.updateQuestionTimes(
                room.id, hostId,
                RoomQuestionTimesRequest(
                    questionIds.map { QuestionTimeEntry(it, timeLimitSec = 5, autoAdvance = false) },
                ),
            )
        }
        sessionService.start(room.id, hostId)
        return room.id
    }
}
