package kr.passmate.hostlevel

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.hostlevel.service.HostGradeService
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
import kr.passmate.session.service.SessionService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 뱃지 컬렉션과 선생님 공개 프로필.
 */
@AutoConfigureMockMvc
@Transactional
class BadgeAndProfileIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var sessionService: SessionService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostGradeService: HostGradeService

    private var hostId: Long = 0
    private var setId: Long = 0
    private lateinit var hostToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("badge-host")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("뱃지 테스트"))
        questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.OX, "참인가", answer = "O", timeLimitSec = 20, points = 50),
        )
        questionSetService.confirm(set.id, hostId)
        setId = set.id
    }

    @Test
    fun `아직 아무것도 못 땄어도 컬렉션 8칸이 전부 보인다`() {
        val body = badges().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("totalCount").asInt()).isEqualTo(8)
        assertThat(body.get("achievedCount").asInt()).isZero()
        assertThat(body.get("badges")).hasSize(8)
        assertThat(body.get("badges").map { it.get("achieved").asBoolean() }).containsOnly(false)
    }

    @Test
    fun `못 딴 뱃지도 목표치를 함께 준다`() {
        val body = badges().andExpect(status().isOk).andReturn().json()

        val streak = body.get("badges").first { it.get("code").asText() == "ACTIVE_30D" }
        assertThat(streak.get("target").asDouble()).isEqualTo(30.0)
        assertThat(streak.get("progress").asInt()).isZero()

        // 별점 조건은 DB 에 10배로 담기지만 응답에서는 4.5 로 돌아온다
        val rating = body.get("badges").first { it.get("code").asText() == "RATING_45" }
        assertThat(rating.get("target").asDouble()).isEqualTo(4.5)
    }

    @Test
    fun `첫 세션을 끝내면 첫 방 개설 뱃지를 딴다`() {
        playSession()

        val body = badges().andExpect(status().isOk).andReturn().json()

        val first = body.get("badges").first { it.get("code").asText() == "FIRST_ROOM" }
        assertThat(first.get("achieved").asBoolean()).isTrue()
        assertThat(first.get("achievedAt").isNull).isFalse()
        assertThat(body.get("achievedCount").asInt()).isEqualTo(1)
        // 딴 것이 맨 앞으로 온다
        assertThat(body.get("badges")[0].get("code").asText()).isEqualTo("FIRST_ROOM")
    }

    @Test
    fun `오늘 세션을 하면 연속 활동이 하루로 잡힌다`() {
        playSession()

        val streak = badges().andReturn().json().get("badges")
            .first { it.get("code").asText() == "ACTIVE_30D" }
        assertThat(streak.get("progress").asInt()).isEqualTo(1)
        assertThat(streak.get("achieved").asBoolean()).isFalse()
    }

    @Test
    fun `한 번 딴 뱃지는 조건이 흔들려도 회수하지 않는다`() {
        playSession()
        assertThat(achievedCodes()).contains("FIRST_ROOM")

        // 30일 뒤 판정 — 최근 활동은 0 이지만 지난 성취는 그대로다
        hostGradeService.evaluate(hostId, java.time.LocalDateTime.now().plusDays(40))

        assertThat(achievedCodes()).contains("FIRST_ROOM")
    }

    @Test
    fun `공개 프로필은 로그인 없이도 볼 수 있다`() {
        playSession()

        val body = mockMvc.perform(get("/users/{id}/profile", hostId))
            .andExpect(status().isOk).andReturn().json()

        assertThat(body.get("nickname").asText()).isEqualTo("badge-host")
        assertThat(body.get("level").asInt()).isEqualTo(1)
        assertThat(body.get("levelName").asText()).isEqualTo("새싹")
        assertThat(body.get("roomsHosted").asInt()).isEqualTo(1)
        assertThat(body.get("totalStudents").asInt()).isEqualTo(1)
        assertThat(body.get("activeSince").isNull).isFalse()
    }

    @Test
    fun `공개 프로필에는 획득한 뱃지만 실린다`() {
        playSession()

        val body = profile().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("badgeCount").asInt()).isEqualTo(1)
        assertThat(body.get("badges")).hasSize(1)
        assertThat(body.get("badges")[0].get("code").asText()).isEqualTo("FIRST_ROOM")
    }

    @Test
    fun `공개 프로필에는 공개한 방만 실린다`() {
        openRoom(title = "공개 방", isPublic = true)
        openRoom(title = "비공개 방", isPublic = false)

        val body = profile().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("openRooms").map { it.get("title").asText() }).containsExactly("공개 방")
    }

    @Test
    fun `끝난 방은 공개 프로필의 참여하기 목록에서 빠진다`() {
        playSession()

        profile().andExpect(status().isOk).andExpect(jsonPath("$.openRooms").isEmpty)
    }

    @Test
    fun `세션을 한 번도 안 한 회원도 프로필이 열린다`() {
        val body = profile().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("level").asInt()).isEqualTo(1)
        assertThat(body.get("roomsHosted").asInt()).isZero()
        assertThat(body.get("badges")).isEmpty()
        // 평가가 없으면 평균은 0.0 이 아니라 빠진다
        assertThat(body.has("avgRating")).isFalse()
    }

    @Test
    fun `없는 회원의 프로필은 404 다`() {
        mockMvc.perform(get("/users/{id}/profile", 999_999))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `로그인하지 않으면 내 뱃지는 볼 수 없다`() {
        mockMvc.perform(get("/users/me/badges")).andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    private fun playSession() {
        val room = roomService.create(hostId, RoomCreateRequest(title = "뱃지 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "뱃지 방", questionSetId = setId))
        participantService.join(room.id, null, JoinRoomRequest(nickname = "학생${room.id}"))
        sessionService.start(room.id, hostId)
        sessionService.end(room.id, hostId)
    }

    private fun openRoom(title: String, isPublic: Boolean) {
        val room = roomService.create(hostId, RoomCreateRequest(title = title, type = RoomType.FREE))
        roomService.update(
            room.id, hostId,
            RoomUpdateRequest(title = title, questionSetId = setId, isPublic = isPublic),
        )
    }

    private fun badges(): ResultActions =
        mockMvc.perform(get("/users/me/badges").header(AUTH, "Bearer $hostToken"))

    private fun profile(): ResultActions = mockMvc.perform(get("/users/{id}/profile", hostId))

    private fun achievedCodes(): List<String> =
        badges().andReturn().json().get("badges")
            .filter { it.get("achieved").asBoolean() }
            .map { it.get("code").asText() }

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
