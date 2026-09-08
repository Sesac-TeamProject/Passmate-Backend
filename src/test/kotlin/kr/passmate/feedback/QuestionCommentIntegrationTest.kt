package kr.passmate.feedback

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 문항 단위 선생님 코멘트 (W-07 우측 패널, B-14).
 * 호스트가 저장하면 방 리포트 문항별 탭과 학생의 내 결과 양쪽에 실리는지 본다.
 */
@AutoConfigureMockMvc
@Transactional
class QuestionCommentIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var hostId: Long = 0
    private var roomId: Long = 0
    private var mcqId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String

    @BeforeEach
    fun setUp() {
        hostId = member("qc-host")
        val studentId = member("qc-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("코멘트 테스트"))
        mcqId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "코멘트 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "코멘트 방", questionSetId = set.id))
        roomId = room.id
        participantService.join(roomId, studentId, JoinRoomRequest(nickname = "학생"))
    }

    @Test
    fun `호스트가 저장한 코멘트가 방 리포트와 학생 결과 양쪽에 실린다`() {
        runSession()

        putComment(hostToken, "전반적으로 개념 정리가 필요합니다")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comment").value("전반적으로 개념 정리가 필요합니다"))
            .andExpect(jsonPath("$.questionId").value(mcqId))

        // 방 리포트 문항별 탭 — 저장창을 다시 열 때 기존 값을 채운다
        mockMvc.perform(get("/rooms/{id}/results", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(jsonPath("$.questions[0].teacherComment").value("전반적으로 개념 정리가 필요합니다"))

        // 학생 결과 — M-06 "선생님 코멘트가 도착하면 여기에 표시돼요" 자리
        mockMvc.perform(get("/rooms/{id}/results/me", roomId).header(AUTH, bearer(studentToken)))
            .andExpect(jsonPath("$.questions[0].teacherComment").value("전반적으로 개념 정리가 필요합니다"))
    }

    @Test
    fun `다시 저장하면 행이 늘지 않고 덮어써진다`() {
        runSession()

        putComment(hostToken, "첫 코멘트").andExpect(status().isOk)
        putComment(hostToken, "고친 코멘트")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comment").value("고친 코멘트"))

        mockMvc.perform(get("/rooms/{id}/results", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(jsonPath("$.questions[0].teacherComment").value("고친 코멘트"))
    }

    @Test
    fun `저장 전에는 코멘트 필드가 없다`() {
        runSession()

        mockMvc.perform(get("/rooms/{id}/results/me", roomId).header(AUTH, bearer(studentToken)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].teacherComment").doesNotExist())
    }

    @Test
    fun `호스트가 아니면 403 이고 이 방에 없는 문항이면 404 다`() {
        runSession()

        putComment(studentToken, "학생이 쓴 코멘트")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_ROOM_HOST"))

        mockMvc.perform(
            put("/rooms/{id}/questions/{q}/comment", roomId, 999_999)
                .header(AUTH, bearer(hostToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"comment":"없는 문항"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))
    }

    @Test
    fun `빈 코멘트는 400 으로 막는다`() {
        runSession()

        putComment(hostToken, " ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    // ---------- helpers ----------

    private fun runSession() {
        mockMvc.perform(post("/rooms/{id}/session/start", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(status().isNoContent)
        mockMvc.perform(
            post("/rooms/{id}/session/questions/{q}/answers", roomId, mcqId)
                .header(AUTH, bearer(studentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"submitted":"찾을 수 없음"}"""),
        ).andExpect(status().isCreated)
        mockMvc.perform(post("/rooms/{id}/session/end", roomId).header(AUTH, bearer(hostToken)))
            .andExpect(status().isNoContent)
    }

    private fun putComment(token: String, comment: String): ResultActions =
        mockMvc.perform(
            put("/rooms/{id}/questions/{q}/comment", roomId, mcqId)
                .header(AUTH, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("comment" to comment))),
        )

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun bearer(token: String) = "Bearer $token"

    @Suppress("unused")
    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
