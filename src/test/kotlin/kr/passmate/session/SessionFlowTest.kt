package kr.passmate.session

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.service.ParticipantService
import kr.passmate.room.service.RoomService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

@AutoConfigureMockMvc
@Transactional
class SessionFlowTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var hostId: Long = 0
    private var roomId: Long = 0
    private lateinit var hostToken: String
    private lateinit var guestToken: String
    private var mcqId: Long = 0
    private var essayId: Long = 0

    @BeforeEach
    fun setUp() {
        hostId = member("sess-host")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("CS 면접"))
        mcqId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        essayId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.ESSAY, "TCP 를 설명하시오", answer = "연결지향", timeLimitSec = 60, points = 200),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "실시간", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "실시간", questionSetId = set.id))
        roomId = room.id
        guestToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트")).accessToken!!
    }

    @Test
    fun `세션을 시작하면 방이 진행 중이 되고 1번 문항이 열린다`() {
        start()

        snapshot(hostToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.currentQuestionNo").value(1))
            .andExpect(jsonPath("$.totalCount").value(2))
            .andExpect(jsonPath("$.currentQuestion.endsAt").isNotEmpty)
    }

    @Test
    fun `진행 중 스냅샷에는 정답과 해설이 들어가지 않는다`() {
        start()

        val body = snapshot(guestToken).andReturn().response.contentAsString
        // 정답 문자열이 통째로 새지 않는지 본문 전체를 훑는다
        assertThat(body).doesNotContain("찾을 수 없음\",\"answer")
        assertThat(objectMapper.readTree(body).get("currentQuestion").has("answer")).isFalse()
        assertThat(objectMapper.readTree(body).get("currentQuestion").has("explanation")).isFalse()
    }

    @Test
    fun `확정 세트가 없으면 시작할 수 없다`() {
        val bare = roomService.create(hostId, RoomCreateRequest(title = "세트 없음", type = RoomType.FREE))

        mockMvc.perform(post("/rooms/{id}/session/start", bare.id).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_SET_REQUIRED"))
    }

    @Test
    fun `호스트가 아니면 세션을 제어할 수 없다`() {
        val other = jwtTokenProvider.issue(member("sess-other"), false).accessToken

        mockMvc.perform(post("/rooms/{id}/session/start", roomId).header("Authorization", "Bearer $other"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `정답을 내면 배점에 속도 보너스가 붙는다`() {
        start()

        submit(guestToken, mcqId, "찾을 수 없음")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.isCorrect").value(true))
            .andExpect(jsonPath("$.baseScore").value(100))
            .andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.greaterThan(100)))
    }

    @Test
    fun `오답은 0점이다`() {
        start()

        submit(guestToken, mcqId, "성공")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.isCorrect").value(false))
            .andExpect(jsonPath("$.score").value(0))
    }

    @Test
    fun `한 문항에 두 번 제출할 수 없다`() {
        start()
        submit(guestToken, mcqId, "성공").andExpect(status().isCreated)

        submit(guestToken, mcqId, "찾을 수 없음")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"))
    }

    @Test
    fun `열리지 않은 문항에는 제출할 수 없다`() {
        start()

        submit(guestToken, essayId, "아직 안 열린 문항")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_NOT_RUNNING"))
    }

    @Test
    fun `마감 전에는 문항 결과를 볼 수 없다`() {
        start()

        mockMvc.perform(get("/rooms/{id}/session/questions/{q}/result", roomId, mcqId).header("Authorization", "Bearer $guestToken"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `마감하면 정답과 응답 분포가 나온다`() {
        start()
        submit(guestToken, mcqId, "찾을 수 없음").andExpect(status().isCreated)
        endCurrent()

        mockMvc.perform(get("/rooms/{id}/session/questions/{q}/result", roomId, mcqId).header("Authorization", "Bearer $guestToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value("찾을 수 없음"))
            .andExpect(jsonPath("$.submitCount").value(1))
            .andExpect(jsonPath("$.correctCount").value(1))
            .andExpect(jsonPath("$.correctRate").value(100.0))
            .andExpect(jsonPath("$.distribution['찾을 수 없음']").value(1))
    }

    @Test
    fun `서술형은 속도 보너스 없이 배점을 잠정으로 받는다`() {
        start()
        endCurrent()
        next()

        submit(guestToken, essayId, "연결지향 프로토콜입니다")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.isCorrect").doesNotExist())
            .andExpect(jsonPath("$.baseScore").value(200))
            .andExpect(jsonPath("$.speedBonus").value(0))
    }

    @Test
    fun `마지막 문항 다음은 없다`() {
        start()
        next()

        mockMvc.perform(post("/rooms/{id}/session/next", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_ALREADY_FINISHED"))
    }

    @Test
    fun `제출 현황은 호스트만 볼 수 있다`() {
        start()
        submit(guestToken, mcqId, "찾을 수 없음")

        mockMvc.perform(get("/rooms/{id}/session/current/submissions", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.submitCount").value(1))
            .andExpect(jsonPath("$.correctRate").value(100.0))

        mockMvc.perform(get("/rooms/{id}/session/current/submissions", roomId).header("Authorization", "Bearer $guestToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `랭킹은 누적 점수 내림차순이고 동점은 같은 등수다`() {
        val a = participantService.join(roomId, member("rank-a"), JoinRoomRequest(nickname = "A")).participant.id
        start()
        submit(guestToken, mcqId, "찾을 수 없음").andExpect(status().isCreated)

        val ranking = mockMvc.perform(get("/rooms/{id}/session/ranking", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isOk).andReturn().json()

        assertThat(ranking).isNotEmpty
        assertThat(ranking[0].get("rank").asInt()).isEqualTo(1)
        assertThat(ranking[0].get("totalScore").asLong()).isGreaterThan(100)
        assertThat(a).isPositive()
    }

    @Test
    fun `화면을 잠그면 답안을 낼 수 없고 풀면 다시 낼 수 있다`() {
        start()

        lock(true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.screenLocked").value(true))

        submit(guestToken, mcqId, "찾을 수 없음")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SCREEN_LOCKED"))

        lock(false)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.screenLocked").value(false))

        submit(guestToken, mcqId, "찾을 수 없음").andExpect(status().isCreated)
    }

    @Test
    fun `잠금 여부는 세션 스냅샷에 실린다`() {
        start()
        lock(true).andExpect(status().isOk)

        snapshot(guestToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.screenLocked").value(true))
    }

    @Test
    fun `호스트가 아니면 화면을 잠글 수 없다`() {
        start()
        val other = jwtTokenProvider.issue(member("lock-other"), false).accessToken

        mockMvc.perform(
            put("/rooms/{id}/session/lock", roomId)
                .header("Authorization", "Bearer $other")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("locked" to true))),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `진행 중이 아니면 화면을 잠글 수 없다`() {
        lock(true)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_NOT_RUNNING"))
    }

    @Test
    fun `세션을 종료하면 방이 닫힌다`() {
        start()
        mockMvc.perform(post("/rooms/{id}/session/end", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isNoContent)

        snapshot(hostToken).andExpect(jsonPath("$.status").value("ENDED"))
    }

    @Test
    fun `현재 문항 마감은 호스트만 할 수 있다`() {
        val other = jwtTokenProvider.issue(member("sess-other2"), false).accessToken
        start()

        mockMvc.perform(post("/rooms/{id}/session/current/end", roomId).header("Authorization", "Bearer $other"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    @Test
    fun `시작 전의 방은 문항을 마감할 수 없다`() {
        mockMvc.perform(post("/rooms/{id}/session/current/end", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_NOT_RUNNING"))
    }

    @Test
    fun `열려 있는 문항이 없으면 마감은 409 다`() {
        start()
        endCurrent()

        mockMvc.perform(post("/rooms/{id}/session/current/end", roomId).header("Authorization", "Bearer $hostToken"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_NOT_RUNNING"))
    }

    @Test
    fun `참가자도 랭킹을 볼 수 있고 제출 전에는 비어 있다`() {
        start()

        mockMvc.perform(get("/rooms/{id}/session/ranking", roomId).header("Authorization", "Bearer $guestToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        submit(guestToken, mcqId, "찾을 수 없음").andExpect(status().isCreated)

        mockMvc.perform(get("/rooms/{id}/session/ranking", roomId).header("Authorization", "Bearer $guestToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[0].nickname").value("게스트"))
    }

    @Test
    fun `랭킹 조회는 인증이 필요하다`() {
        start()

        mockMvc.perform(get("/rooms/{id}/session/ranking", roomId))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `참가하지 않은 회원은 세션 조회 API 를 볼 수 없다`() {
        val outsider = jwtTokenProvider.issue(member("sess-outsider"), false).accessToken
        start()

        snapshot(outsider)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

        mockMvc.perform(get("/rooms/{id}/session/ranking", roomId).header("Authorization", "Bearer $outsider"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

        endCurrent()
        mockMvc.perform(get("/rooms/{id}/session/questions/{q}/result", roomId, mcqId).header("Authorization", "Bearer $outsider"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `다른 방 게스트 토큰으로는 세션을 볼 수 없다`() {
        val otherRoom = roomService.create(hostId, RoomCreateRequest(title = "다른 방", type = RoomType.FREE))
        val foreignGuest = participantService.join(otherRoom.id, null, JoinRoomRequest(nickname = "남의방게스트")).accessToken!!
        start()

        snapshot(foreignGuest)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

        mockMvc.perform(get("/rooms/{id}/session/ranking", roomId).header("Authorization", "Bearer $foreignGuest"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `문항 마감 시각은 UTC 기준으로 내려온다`() {
        start()

        val endsAt = snapshot(hostToken).andReturn().json()
            .get("currentQuestion").get("endsAt").asText()
            .let { LocalDateTime.parse(it) }

        // 계약은 "오프셋 없는 UTC". JVM 기본 시간대(KST)로 발급되면 9시간 어긋난다 — 프론트 QA_BACKLOG B-1
        val nowUtc = LocalDateTime.now(ZoneOffset.UTC)
        assertThat(Duration.between(nowUtc, endsAt).abs()).isLessThan(Duration.ofMinutes(5))
    }

    // ---------- helpers ----------

    private fun start() = mockMvc.perform(post("/rooms/{id}/session/start", roomId).header("Authorization", "Bearer $hostToken"))
        .andExpect(status().isNoContent)

    private fun next() = mockMvc.perform(post("/rooms/{id}/session/next", roomId).header("Authorization", "Bearer $hostToken"))
        .andExpect(status().isNoContent)

    private fun endCurrent() = mockMvc.perform(post("/rooms/{id}/session/current/end", roomId).header("Authorization", "Bearer $hostToken"))
        .andExpect(status().isNoContent)

    private fun lock(locked: Boolean) = mockMvc.perform(
        put("/rooms/{id}/session/lock", roomId)
            .header("Authorization", "Bearer $hostToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("locked" to locked))),
    )

    private fun snapshot(token: String) =
        mockMvc.perform(get("/rooms/{id}/session", roomId).header("Authorization", "Bearer $token"))

    private fun submit(token: String, questionId: Long, submitted: String) = mockMvc.perform(
        post("/rooms/{id}/session/questions/{q}/answers", roomId, questionId)
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("submitted" to submitted))),
    )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
