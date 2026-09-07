package kr.passmate.room

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.rating.domain.RoomRating
import kr.passmate.rating.repository.RoomRatingRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 내가 만든 방 목록. 끝낸 방 하나와 아직 안 연 방 하나를 두고 두 섹션이 갈리는지 본다.
 */
@AutoConfigureMockMvc
@Transactional
class HostedRoomIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var roomRatingRepository: RoomRatingRepository

    private var hostId: Long = 0
    private var studentId: Long = 0
    private var setId: Long = 0
    private var questionId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("host-rooms")
        studentId = member("host-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("내 방 테스트"))
        setId = set.id
        questionId = questionSetService.addQuestion(
            setId, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(setId, hostId)
    }

    @Test
    fun `기록이 없으면 두 목록 다 비어 있고 등급은 값이 없다`() {
        val body = hosted().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("active")).isEmpty()
        assertThat(body.get("ended")).isEmpty()
        assertThat(body.get("reputation").get("hostedSessionCount").asLong()).isZero()
        // hostlevel 기능 전까지 등급은 0 이 아니라 값이 없다
        assertThat(body.get("reputation").has("level")).isFalse()
        assertThat(body.get("reputation").has("averageStars")).isFalse()
    }

    @Test
    fun `아직 안 끝난 방은 PIN 과 함께 진행 중 목록에 담긴다`() {
        val room = roomService.create(hostId, RoomCreateRequest(title = "예정된 방", type = RoomType.FREE))

        val body = hosted().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("ended")).isEmpty()
        val active = body.get("active").single()
        assertThat(active.get("roomId").asLong()).isEqualTo(room.id)
        assertThat(active.get("status").asText()).isEqualTo("WAITING")
        // 내 방이라 호스트에게는 PIN 을 보여 준다(공개 목록과 다르다)
        assertThat(active.get("pin").asText()).hasSize(6)
    }

    @Test
    fun `취소한 방은 진행 중이 아니라 종료 목록에 취소 상태로 담긴다`() {
        // 와이어프레임(W-09·M-13)에는 진행 중·종료 두 상태뿐이라 취소는 종료 쪽에 붙인다(2026-09-07 결정).
        // 종료 여부만으로 가르면 취소된 방이 진행 중에 섞여 "진행 중인 방 열기"가 취소된 방을 열었다
        val canceled = roomService.create(hostId, RoomCreateRequest(title = "취소한 방", type = RoomType.FREE))
        participantService.join(canceled.id, studentId, JoinRoomRequest(nickname = "먼저 들어온 학생"))
        roomService.close(canceled.id, hostId)   // 시작 전 종료 = CANCELED
        val playedId = playRoom("끝난 방")

        val body = hosted().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("active")).isEmpty()
        val ended = body.get("ended")
        assertThat(ended).hasSize(2)

        val canceledRow = ended.first { it.get("roomId").asLong() == canceled.id }
        assertThat(canceledRow.get("status").asText()).isEqualTo("CANCELED")
        // 세션이 없었으니 성적·별점은 없다. 대기실에 들어왔던 사람도 세지 않는다 — "학생 1명"으로 읽히면 안 된다
        assertThat(canceledRow.get("studentCount").asLong()).isZero()
        assertThat(canceledRow.has("correctRate")).isFalse()
        assertThat(canceledRow.has("averageStars")).isFalse()
        assertThat(canceledRow.get("endedAt").isNull).isFalse()

        val playedRow = ended.first { it.get("roomId").asLong() == playedId }
        assertThat(playedRow.get("status").asText()).isEqualTo("ENDED")
        assertThat(playedRow.get("correctRate").asDouble()).isEqualTo(100.0)
    }

    @Test
    fun `끝난 방은 학생 수와 평균 정답률이 함께 나온다`() {
        val roomId = playRoom("끝난 방")

        val ended = hosted().andExpect(status().isOk).andReturn().json().get("ended").single()

        assertThat(ended.get("roomId").asLong()).isEqualTo(roomId)
        assertThat(ended.get("studentCount").asLong()).isEqualTo(1)
        // 한 명이 한 문항을 맞혔으므로 100%
        assertThat(ended.get("correctRate").asDouble()).isEqualTo(100.0)
        assertThat(ended.get("endedAt").isNull).isFalse()
        assertThat(ended.get("ratingCount").asInt()).isZero()
    }

    @Test
    fun `받은 별점이 명성 요약과 방별 평균에 모두 반영된다`() {
        val roomId = playRoom("평가 받은 방")
        val participantId = participantService.getJoinedParticipantOfUser(roomId, studentId).id
        roomRatingRepository.saveAndFlush(
            RoomRating(roomId = roomId, participantId = participantId, hostUserId = hostId, stars = 4),
        )

        val body = hosted().andExpect(status().isOk).andReturn().json()

        assertThat(body.get("reputation").get("averageStars").asDouble()).isEqualTo(4.0)
        assertThat(body.get("reputation").get("ratingCount").asInt()).isEqualTo(1)
        val ended = body.get("ended").single()
        assertThat(ended.get("averageStars").asDouble()).isEqualTo(4.0)
        assertThat(ended.get("ratingCount").asInt()).isEqualTo(1)
    }

    @Test
    fun `남이 만든 방은 내 목록에 들어오지 않는다`() {
        val other = member("host-other")
        roomService.create(other, RoomCreateRequest(title = "남의 방", type = RoomType.FREE))

        hosted()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active.length()").value(0))
            .andExpect(jsonPath("$.ended.length()").value(0))
    }

    // ---------- helpers ----------

    /** 방을 열어 학생 하나가 정답을 내고 끝낸다. */
    private fun playRoom(title: String): Long {
        val room = roomService.create(hostId, RoomCreateRequest(title = title, type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = title, questionSetId = setId))
        participantService.join(room.id, studentId, JoinRoomRequest(nickname = "학생"))

        host(post("/rooms/{id}/session/start", room.id)).andExpect(status().isNoContent)
        mockMvc.perform(
            post("/rooms/{id}/session/questions/{q}/answers", room.id, questionId)
                .header(AUTH, "Bearer $studentToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("submitted" to "찾을 수 없음"))),
        ).andExpect(status().isCreated)
        host(post("/rooms/{id}/session/end", room.id)).andExpect(status().isNoContent)
        return room.id
    }

    private fun host(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) =
        mockMvc.perform(builder.header(AUTH, "Bearer $hostToken"))

    private fun hosted() = mockMvc.perform(get("/users/me/rooms/hosted").header(AUTH, "Bearer $hostToken"))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
