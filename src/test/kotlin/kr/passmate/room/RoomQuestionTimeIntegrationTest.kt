package kr.passmate.room

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.service.RoomService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 방 단위 문항별 시간 오버라이드(W-02b, 웹 버그 리포트 B-18 결정 (b)).
 *
 * 확정 세트는 불변이라 세트를 고치지 않고 방이 시간을 덮어쓴다. 덮어쓴 값이
 * 조회·방 상세·세션 시작(session_question)까지 따라가는지 한 바퀴 돌려 본다.
 */
@AutoConfigureMockMvc
@Transactional
class RoomQuestionTimeIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @PersistenceContext private lateinit var entityManager: EntityManager

    private var hostId: Long = 0
    private var roomId: Long = 0
    private var setId: Long = 0
    private var mcqId: Long = 0
    private var essayId: Long = 0
    private lateinit var hostToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("qt-host")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        otherToken = jwtTokenProvider.issue(member("qt-other"), false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("시간 테스트"))
        setId = set.id
        mcqId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        essayId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.ESSAY, "TCP 를 설명하시오", answer = "연결지향", timeLimitSec = 60, points = 200),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "시간 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "시간 방", questionSetId = set.id))
        roomId = room.id
    }

    @Test
    fun `호스트가 문항별 시간을 덮어쓰면 조회·방 상세·세션 시작에 반영된다`() {
        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questionSetId").value(setId))
            .andExpect(jsonPath("$.questions[0].questionId").value(mcqId))
            .andExpect(jsonPath("$.questions[0].defaultTimeLimitSec").value(30))
            .andExpect(jsonPath("$.questions[0].timeLimitSec").value(20))
            .andExpect(jsonPath("$.questions[0].overridden").value(true))
            .andExpect(jsonPath("$.questions[1].timeLimitSec").value(60))
            .andExpect(jsonPath("$.questions[1].overridden").value(false))
            .andExpect(jsonPath("$.estimatedSeconds").value(80))
            // 프로젝터에 뜰 수 있는 화면이라 정답·해설은 싣지 않는다
            .andExpect(jsonPath("$.questions[0].answer").doesNotExist())

        // JSON 컬럼 왕복을 실제로 태운다 — 영속성 컨텍스트에 남은 객체를 그대로 읽으면 직렬화가 검증되지 않는다
        entityManager.flush()
        entityManager.clear()

        getTimes(hostToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].timeLimitSec").value(20))
            .andExpect(jsonPath("$.questions[0].overridden").value(true))

        // 방 상세의 KPI 도 이 방 기준 값이다
        mockMvc.perform(get("/rooms/{id}", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.minTimeLimitSec").value(20))
            .andExpect(jsonPath("$.maxTimeLimitSec").value(60))
            .andExpect(jsonPath("$.estimatedSeconds").value(80))

        // 세션 시작 시 session_question 으로 복사되는 값이 덮어쓴 값이다
        mockMvc.perform(post("/rooms/{id}/session/start", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/rooms/{id}/session", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentQuestion.questionId").value(mcqId))
            .andExpect(jsonPath("$.currentQuestion.timeLimitSec").value(20))
    }

    @Test
    fun `자동 넘김 토글이 저장되고 빠진 문항은 꺼진다`() {
        // W-02b 의 "자동 넘김" 열 — 시간과 같은 화면에서 함께 저장된다(2026-09-07 결정)
        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20,"autoAdvance":true},{"questionId":$essayId,"timeLimitSec":90}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].autoAdvance").value(true))
            .andExpect(jsonPath("$.questions[1].autoAdvance").value(false))

        // 전체 교체 — 본문에서 빠지면 자동 넘김도 꺼진다
        putTimes(hostToken, """{"times":[{"questionId":$essayId,"timeLimitSec":90,"autoAdvance":true}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].autoAdvance").value(false))
            .andExpect(jsonPath("$.questions[1].autoAdvance").value(true))

        getTimes(hostToken)
            .andExpect(jsonPath("$.questions[1].autoAdvance").value(true))
    }

    @Test
    fun `빈 목록을 보내면 세트 기본값으로 돌아간다`() {
        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20},{"questionId":$essayId,"timeLimitSec":90}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.estimatedSeconds").value(110))

        // PUT 은 전체 교체 — 빠진 문항은 기본값으로 돌아간다
        putTimes(hostToken, """{"times":[{"questionId":$essayId,"timeLimitSec":90}]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].timeLimitSec").value(30))
            .andExpect(jsonPath("$.questions[0].overridden").value(false))
            .andExpect(jsonPath("$.questions[1].timeLimitSec").value(90))

        putTimes(hostToken, """{"times":[]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[1].timeLimitSec").value(60))
            .andExpect(jsonPath("$.questions[1].overridden").value(false))
            .andExpect(jsonPath("$.estimatedSeconds").value(90))
    }

    @Test
    fun `세트에 없는 문항이나 범위 밖 시간은 400 이다`() {
        putTimes(hostToken, """{"times":[{"questionId":999999,"timeLimitSec":20}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))

        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":3}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))

        // 같은 문항을 두 번 보내면 어느 값이 맞는지 알 수 없다
        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20},{"questionId":$mcqId,"timeLimitSec":40}]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))

        // 어느 것도 저장되지 않았다
        getTimes(hostToken)
            .andExpect(jsonPath("$.questions[0].overridden").value(false))
    }

    @Test
    fun `호스트가 아니면 403 이고 세트를 연결하지 않았으면 409 다`() {
        putTimes(otherToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20}]}""")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
        getTimes(otherToken)
            .andExpect(status().isForbidden)

        val bare = roomService.create(hostId, RoomCreateRequest(title = "세트 없는 방", type = RoomType.FREE)).id
        mockMvc.perform(get("/rooms/{id}/question-times", bare).header(AUTH, bearer(hostToken)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_REQUIRED"))
    }

    @Test
    fun `세션이 시작되면 시간을 바꿀 수 없다`() {
        mockMvc.perform(post("/rooms/{id}/session/start", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(status().isNoContent)

        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20}]}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    @Test
    fun `세트를 바꾸면 덮어쓴 시간이 비워진다`() {
        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20}]}""")
            .andExpect(status().isOk)

        val other = questionSetService.create(hostId, QuestionSetCreateRequest("다른 세트"))
        questionSetService.addQuestion(other.id, hostId, QuestionRequest(QuestionType.OX, "OX", answer = "O", timeLimitSec = 45))
        questionSetService.confirm(other.id, hostId)
        roomService.update(roomId, hostId, RoomUpdateRequest(title = "시간 방", questionSetId = other.id))

        // 예전 세트의 문항 id 를 가리키던 값은 의미가 없다 — 새 세트는 전부 기본값
        getTimes(hostToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questionSetId").value(other.id))
            .andExpect(jsonPath("$.questions.length()").value(1))
            .andExpect(jsonPath("$.questions[0].timeLimitSec").value(45))
            .andExpect(jsonPath("$.questions[0].overridden").value(false))

        putTimes(hostToken, """{"times":[{"questionId":$mcqId,"timeLimitSec":20}]}""")
            .andExpect(status().isBadRequest)
    }

    // ---------- helpers ----------

    private fun getTimes(token: String): ResultActions =
        mockMvc.perform(get("/rooms/{id}/question-times", roomId).header(AUTH, bearer(token)))

    private fun putTimes(token: String, body: String): ResultActions =
        mockMvc.perform(
            put("/rooms/{id}/question-times", roomId).header(AUTH, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body),
        )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun bearer(token: String) = "Bearer $token"

    private companion object {
        const val AUTH = "Authorization"
    }
}
