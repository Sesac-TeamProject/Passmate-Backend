package kr.passmate.hostlevel

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.hostlevel.repository.HostProfileRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 등급 API 와 집계 경로. 등급표 판정 자체는 [kr.passmate.hostlevel.service.HostLevelDeciderTest]
 * 가 숫자만 바꿔 가며 훑으므로, 여기서는 **집계가 실제 데이터에서 제대로 나오는지**를 본다.
 */
@AutoConfigureMockMvc
@Transactional
class HostGradeIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var sessionService: SessionService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var hostGradeService: HostGradeService
    @Autowired private lateinit var hostProfileRepository: HostProfileRepository

    private var hostId: Long = 0
    private var setId: Long = 0
    private lateinit var hostToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("grade-host")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("등급 테스트"))
        questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.OX, "참인가", answer = "O", timeLimitSec = 20, points = 50),
        )
        questionSetService.confirm(set.id, hostId)
        setId = set.id
    }

    @Test
    fun `세션을 한 번도 안 했으면 Lv1 로 시작한다`() {
        val body = grade().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("level").asInt()).isEqualTo(1)
        assertThat(body.get("levelName").asText()).isEqualTo("새싹")
        assertThat(body.get("roomsHosted").asInt()).isZero()
        assertThat(body.get("ratingCount").asInt()).isZero()
        // 받은 평가가 없으면 평균은 0.0 이 아니라 빠진다
        assertThat(body.has("avgRating")).isFalse()
    }

    @Test
    fun `다음 등급 조건과 진행도를 함께 준다`() {
        val body = grade().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("nextLevel").asInt()).isEqualTo(2)
        assertThat(body.get("nextLevelName").asText()).isEqualTo("성장")

        val requirements = body.get("nextRequirements")
        assertThat(requirements.map { it.get("type").asText() })
            .containsExactly("ROOMS_HOSTED", "TOTAL_STUDENTS")
        assertThat(requirements[0].get("target").asDouble()).isEqualTo(10.0)
        assertThat(requirements[0].get("met").asBoolean()).isFalse()
        assertThat(body.get("nextLevelProgress").asDouble()).isZero()
    }

    @Test
    fun `Lv1 은 유지 조건이 없어 비어 있다`() {
        grade()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.maintenance").doesNotExist())
            .andExpect(jsonPath("$.unlocked[0]").value("프로필 뱃지"))
    }

    @Test
    fun `세션을 끝내면 판정이 자동으로 돈다`() {
        playSession()

        val body = grade().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("roomsHosted").asInt()).isEqualTo(1)
        assertThat(body.get("totalStudents").asInt()).isEqualTo(1)
        assertThat(body.get("lastEvaluatedAt").isNull).isFalse()
        // 방 운영 1회는 Lv.2 조건(10회)에 한참 못 미친다
        assertThat(body.get("level").asInt()).isEqualTo(1)
        assertThat(body.get("nextLevelProgress").asDouble()).isGreaterThan(0.0)
    }

    @Test
    fun `세션 세 번을 돌리면 운영 횟수와 학생 수가 쌓인다`() {
        repeat(3) { playSession() }

        val body = grade().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("roomsHosted").asInt()).isEqualTo(3)
        assertThat(body.get("totalStudents").asInt()).isEqualTo(3)
    }

    @Test
    fun `시작하지 않고 닫은 방은 운영 횟수에 들어가지 않는다`() {
        val room = roomService.create(hostId, RoomCreateRequest(title = "안 연 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "안 연 방", questionSetId = setId))
        roomService.close(room.id, hostId)

        hostGradeService.evaluate(hostId)

        assertThat(hostProfileRepository.findByUserId(hostId)!!.roomsHosted).isZero()
    }

    @Test
    fun `관리자는 특정 회원만 판정할 수 있다`() {
        playSession()

        evaluateAsAdmin(hostId.toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.evaluated").value(1))
    }

    @Test
    fun `관리자는 세션을 진행한 호스트 전체를 판정할 수 있다`() {
        playSession()

        evaluateAsAdmin(null)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.evaluated").value(1))
    }

    @Test
    fun `관리자가 아니면 판정을 실행할 수 없다`() {
        mockMvc.perform(post("/admin/grades/evaluate").header(AUTH, "Bearer $hostToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `내가 만든 방 요약에도 등급이 실린다`() {
        playSession()

        mockMvc.perform(get("/users/me/rooms/hosted").header(AUTH, "Bearer $hostToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reputation.level").value(1))
            .andExpect(jsonPath("$.reputation.hostedSessionCount").value(1))
    }

    @Test
    fun `로그인하지 않으면 등급을 볼 수 없다`() {
        mockMvc.perform(get("/users/me/grade")).andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    /** 실제 세션 1회 — 학생 1명이 들어오고 호스트가 시작·종료한다. */
    private fun playSession() {
        val room = roomService.create(hostId, RoomCreateRequest(title = "등급 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "등급 방", questionSetId = setId))
        participantService.join(room.id, null, JoinRoomRequest(nickname = "학생${room.id}"))
        sessionService.start(room.id, hostId)
        sessionService.end(room.id, hostId)
    }

    private fun grade(): ResultActions =
        mockMvc.perform(get("/users/me/grade").header(AUTH, "Bearer $hostToken"))

    private fun evaluateAsAdmin(userId: String?): ResultActions {
        val adminToken = jwtTokenProvider.issue(member("grade-admin"), true).accessToken
        val request = post("/admin/grades/evaluate").header(AUTH, "Bearer $adminToken")
        userId?.let { request.param("userId", it) }
        return mockMvc.perform(request)
    }

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
