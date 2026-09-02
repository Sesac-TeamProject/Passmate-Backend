package kr.passmate.rating

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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 평가 마감. 창을 0시간으로 좁혀 두면 종료 직후가 곧 마감 이후다 —
 * 24시간을 실제로 기다리지 않고 만료 경로를 밟는다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["passmate.policy.rating-window-hours=0"])
class RoomRatingWindowIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    @Test
    fun `평가 기간이 지나면 제출할 수 없다`() {
        val hostId = member("window-host")
        val studentId = member("window-student")
        val hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        val studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("마감 테스트"))
        val questionId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "마감 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "마감 방", questionSetId = set.id))
        participantService.join(room.id, studentId, JoinRoomRequest(nickname = "학생"))

        mockMvc.perform(post("/rooms/{id}/session/start", room.id).header(AUTH, "Bearer $hostToken"))
            .andExpect(status().isNoContent)
        mockMvc.perform(
            post("/rooms/{id}/session/questions/{q}/answers", room.id, questionId)
                .header(AUTH, "Bearer $studentToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("submitted" to "찾을 수 없음"))),
        ).andExpect(status().isCreated)
        mockMvc.perform(post("/rooms/{id}/session/end", room.id).header(AUTH, "Bearer $hostToken"))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/rooms/{id}/ratings", room.id)
                .header(AUTH, "Bearer $studentToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("stars" to 5))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RATING_WINDOW_CLOSED"))
    }

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    companion object {
        private const val AUTH = "Authorization"
    }
}
