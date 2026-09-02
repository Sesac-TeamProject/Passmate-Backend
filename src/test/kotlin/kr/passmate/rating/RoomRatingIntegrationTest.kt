package kr.passmate.rating

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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 세션 평가 제출·조회. 객관식 1문항짜리 방을 한 바퀴 돌려 놓고 확인한다.
 *
 * 참가자는 셋 — 회원 학생·게스트는 답을 내고, 구경꾼은 아무것도 내지 않는다(미제출 경로용).
 */
@AutoConfigureMockMvc
@Transactional
class RoomRatingIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var hostId: Long = 0
    private var roomId: Long = 0
    private var questionId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String
    private lateinit var guestToken: String
    private lateinit var idleToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("rate-host")
        val studentId = member("rate-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("평가 테스트"))
        questionId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "평가 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "평가 방", questionSetId = set.id))
        roomId = room.id

        participantService.join(roomId, studentId, JoinRoomRequest(nickname = "학생"))
        guestToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트")).accessToken!!
        idleToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "구경꾼")).accessToken!!
    }

    @Test
    fun `답안을 낸 참가자는 별점과 태그와 후기를 남긴다`() {
        runSession()

        val body = rate(studentToken, 5, listOf("CLEAR_EXPLANATION", "GOOD_PACING"), "설명이 좋았어요")
            .andExpect(status().isCreated).andReturn().json()

        assertThat(body.get("stars").asInt()).isEqualTo(5)
        assertThat(body.get("tags").map { it.asText() }).containsExactly("CLEAR_EXPLANATION", "GOOD_PACING")
        assertThat(body.get("comment").asText()).isEqualTo("설명이 좋았어요")
        assertThat(body.get("id").asLong()).isPositive()
    }

    @Test
    fun `무료 방은 게스트도 평가할 수 있다`() {
        runSession()

        rate(guestToken, 4).andExpect(status().isCreated)
    }

    @Test
    fun `같은 사람이 두 번 평가할 수 없다`() {
        runSession()
        rate(studentToken, 5).andExpect(status().isCreated)

        rate(studentToken, 3)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_RATED"))
    }

    @Test
    fun `세션이 끝나기 전에는 평가할 수 없다`() {
        start()
        submit(studentToken)

        rate(studentToken, 5)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_NOT_ENDED"))
    }

    @Test
    fun `답안을 한 개도 내지 않았으면 평가할 수 없다`() {
        runSession()

        rate(idleToken, 5)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("RATING_NOT_ALLOWED"))
    }

    @Test
    fun `별점이 1에서 5 밖이면 거절한다`() {
        runSession()

        rate(studentToken, 0).andExpect(status().isBadRequest)
        rate(studentToken, 6).andExpect(status().isBadRequest)
    }

    @Test
    fun `그 방에 들어온 적 없는 사람은 평가할 수 없다`() {
        runSession()
        val outsiderToken = jwtTokenProvider.issue(member("rate-outsider"), false).accessToken

        rate(outsiderToken, 5)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_FOUND"))
    }

    @Test
    fun `호스트는 평균과 별점 분포와 태그 집계를 한 번에 본다`() {
        runSession()
        rate(studentToken, 5, listOf("CLEAR_EXPLANATION", "GOOD_PACING")).andExpect(status().isCreated)
        rate(guestToken, 4, listOf("CLEAR_EXPLANATION"), "괜찮았습니다").andExpect(status().isCreated)

        val body = list(hostToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("totalCount").asInt()).isEqualTo(2)
        assertThat(body.get("averageStars").asDouble()).isEqualTo(4.5)
        // 1~5 를 항상 다 채운다 — 0건이어도 자리가 남아야 막대그래프가 그려진다
        val stars = body.get("starCounts")
        assertThat(stars.size()).isEqualTo(5)
        assertThat(stars.get("1").asInt()).isZero()
        assertThat(stars.get("4").asInt()).isEqualTo(1)
        assertThat(stars.get("5").asInt()).isEqualTo(1)

        val tags = body.get("tagCounts")
        assertThat(tags).hasSize(2)
        assertThat(tags[0].get("tag").asText()).isEqualTo("CLEAR_EXPLANATION")
        assertThat(tags[0].get("count").asInt()).isEqualTo(2)
        assertThat(tags[0].get("label").asText()).isEqualTo("설명이 명확해요")

        // 최근 순 — 게스트가 나중에 냈다
        val ratings = body.get("ratings")
        assertThat(ratings).hasSize(2)
        assertThat(ratings[0].get("comment").asText()).isEqualTo("괜찮았습니다")
        // 누가 남겼는지는 나가지 않는다
        assertThat(ratings[0].has("nickname")).isFalse()
        assertThat(ratings[0].has("participantId")).isFalse()
    }

    @Test
    fun `평가가 하나도 없으면 평균은 0이 아니라 비어 있다`() {
        runSession()

        val body = list(hostToken).andExpect(status().isOk).andReturn().json()

        assertThat(body.get("totalCount").asInt()).isZero()
        // non_null 직렬화라 평균은 필드 자체가 빠진다 — 0.0 은 "별 0개"로 읽힌다
        assertThat(body.has("averageStars")).isFalse()
        assertThat(body.get("tagCounts")).isEmpty()
    }

    @Test
    fun `학생은 남겨진 후기를 볼 수 없다`() {
        runSession()
        rate(studentToken, 5, comment = "호스트만 볼 후기").andExpect(status().isCreated)

        list(studentToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))
    }

    // ---------- helpers ----------

    /** 학생·게스트가 답을 내고 세션을 끝낸다. 구경꾼은 아무것도 내지 않는다. */
    private fun runSession() {
        start()
        submit(studentToken)
        submit(guestToken)
        endSession()
    }

    private fun start() = mockMvc.perform(post("/rooms/{id}/session/start", roomId).header(AUTH, bearer(hostToken)))
        .andExpect(status().isNoContent)

    private fun endSession() = mockMvc.perform(post("/rooms/{id}/session/end", roomId).header(AUTH, bearer(hostToken)))
        .andExpect(status().isNoContent)

    private fun submit(token: String) = mockMvc.perform(
        post("/rooms/{id}/session/questions/{q}/answers", roomId, questionId)
            .header(AUTH, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("submitted" to "찾을 수 없음"))),
    ).andExpect(status().isCreated)

    private fun rate(
        token: String,
        stars: Int,
        tags: List<String>? = null,
        comment: String? = null,
    ): ResultActions {
        val payload = buildMap<String, Any> {
            put("stars", stars)
            tags?.let { put("tags", it) }
            comment?.let { put("comment", it) }
        }
        return mockMvc.perform(
            post("/rooms/{id}/ratings", roomId)
                .header(AUTH, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)),
        )
    }

    private fun list(token: String): ResultActions =
        mockMvc.perform(get("/rooms/{id}/ratings", roomId).header(AUTH, bearer(token)))

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun bearer(token: String) = "Bearer $token"

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    companion object {
        private const val AUTH = "Authorization"
    }
}
