package kr.passmate.voicehint

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.common.storage.StorageClient
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
import kr.passmate.support.FakeStorageClient
import kr.passmate.support.FakeStorageConfig
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 실시간 음성 힌트. S3 는 Fake 라 **실제 버킷을 건드리지 않는다**.
 */
@AutoConfigureMockMvc
@Transactional
@Import(FakeStorageConfig::class)
class VoiceHintIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var storageClient: StorageClient

    private val fake: FakeStorageClient get() = storageClient as FakeStorageClient

    private var hostId: Long = 0
    private var roomId: Long = 0
    private var firstId: Long = 0
    private var secondId: Long = 0
    private lateinit var hostToken: String
    private lateinit var studentToken: String
    private lateinit var outsiderToken: String

    @BeforeEach
    fun setUp() {
        fake.reset()
        hostId = member("hint-host")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        outsiderToken = jwtTokenProvider.issue(member("hint-outsider"), false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("힌트 테스트"))
        firstId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        secondId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.OX, "TCP 는 연결지향이다", answer = "O", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "힌트 방", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "힌트 방", questionSetId = set.id))
        roomId = room.id
        studentToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "학생")).accessToken!!
    }

    @Test
    fun `호스트가 올린 클립이 저장되고 재생 주소가 나온다`() {
        start()

        val body = publish(hostToken, audio(), durationMs = 2400)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.questionId").value(firstId))
            .andExpect(jsonPath("$.orderNo").value(1))
            .andExpect(jsonPath("$.durationMs").value(2400))
            .andReturn().json()

        // 키는 방별로 갈라 두고, 재생 주소는 그 키를 서명해 만든다
        val key = fake.uploaded.keys.single()
        assertThat(key).startsWith("rooms/$roomId/hints/").endsWith(".webm")
        assertThat(fake.lastContentType).isEqualTo("audio/webm")
        assertThat(body.get("audioUrl").asText()).isEqualTo("${FakeStorageClient.BASE_URL}/$key?signature=fake")
    }

    @Test
    fun `열린 문항이 없으면 힌트를 보낼 수 없다`() {
        publish(hostToken, audio())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_NOT_RUNNING"))

        assertThat(fake.uploaded).isEmpty()
    }

    @Test
    fun `호스트가 아니면 힌트를 보낼 수 없다`() {
        start()

        publish(studentToken, audio())
            .andExpect(status().isForbidden)

        assertThat(fake.uploaded).isEmpty()
    }

    @Test
    fun `오디오가 아니면 올릴 수 없다`() {
        start()

        publish(hostToken, MockMultipartFile("file", "note.txt", "text/plain", "hello".toByteArray()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("오디오 파일만 올릴 수 있습니다."))

        assertThat(fake.uploaded).isEmpty()
    }

    @Test
    fun `빈 파일은 올릴 수 없다`() {
        start()

        publish(hostToken, MockMultipartFile("file", "empty.webm", "audio/webm", ByteArray(0)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("녹음 파일이 비어 있습니다."))
    }

    @Test
    fun `상한을 넘는 파일은 올릴 수 없다`() {
        start()

        val tooBig = MockMultipartFile("file", "long.webm", "audio/webm", ByteArray(11 * 1024 * 1024))
        publish(hostToken, tooBig)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))

        assertThat(fake.uploaded).isEmpty()
    }

    @Test
    fun `저장에 실패하면 502 로 알리고 기록을 남기지 않는다`() {
        start()
        fake.failOnUpload = true

        publish(hostToken, audio())
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))

        list(hostToken).andExpect(jsonPath("$.totalCount").value(0))
    }

    @Test
    fun `학생은 목록으로 다시 들을 수 있다`() {
        start()
        publish(hostToken, audio()).andExpect(status().isCreated)

        list(studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.hints[0].audioUrl").isNotEmpty)
            .andExpect(jsonPath("$.hints[0].questionId").value(firstId))
    }

    @Test
    fun `문항으로 좁히면 그 문항 힌트만 나온다`() {
        start()
        publish(hostToken, audio()).andExpect(status().isCreated)
        next()
        publish(hostToken, audio()).andExpect(status().isCreated)

        list(hostToken).andExpect(jsonPath("$.totalCount").value(2))

        list(hostToken, questionId = secondId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.hints[0].questionId").value(secondId))
            .andExpect(jsonPath("$.hints[0].orderNo").value(2))
    }

    @Test
    fun `그 방 사람이 아니면 힌트를 들을 수 없다`() {
        start()
        publish(hostToken, audio()).andExpect(status().isCreated)

        list(outsiderToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    // ---------- helpers ----------

    private fun audio() = MockMultipartFile("file", "hint.webm", "audio/webm", byteArrayOf(1, 2, 3, 4))

    private fun publish(token: String, file: MockMultipartFile, durationMs: Int? = null): ResultActions {
        val request = multipart("/rooms/{id}/session/hints", roomId)
            .file(file)
            .header(AUTH, "Bearer $token")
        durationMs?.let { request.param("durationMs", it.toString()) }
        return mockMvc.perform(request)
    }

    private fun list(token: String, questionId: Long? = null): ResultActions {
        val request = get("/rooms/{id}/session/hints", roomId).header(AUTH, "Bearer $token")
        questionId?.let { request.param("questionId", it.toString()) }
        return mockMvc.perform(request)
    }

    private fun start() = mockMvc.perform(post("/rooms/{id}/session/start", roomId).header(AUTH, "Bearer $hostToken"))
        .andExpect(status().isNoContent)

    private fun next() = mockMvc.perform(post("/rooms/{id}/session/next", roomId).header(AUTH, "Bearer $hostToken"))
        .andExpect(status().isNoContent)

    private fun member(key: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, key, "$key@example.com", key, null).user.id

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    private companion object {
        const val AUTH = "Authorization"
    }
}
