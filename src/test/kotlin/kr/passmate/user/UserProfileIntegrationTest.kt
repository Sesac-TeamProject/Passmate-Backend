package kr.passmate.user

import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.repository.RoomRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class UserProfileIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var roomRepository: RoomRepository

    private var userId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val outcome = userService.loginOrRegister(
            AuthProvider.GOOGLE, "me-user", "me@example.com", "혜림", "https://img.example/a.png",
        )
        userId = outcome.user.id
        token = jwtTokenProvider.issue(userId, outcome.user.isAdmin).accessToken
    }

    @Test
    fun `프로필과 코인 잔액을 준다`() {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.nickname").value("혜림"))
            .andExpect(jsonPath("$.email").value("me@example.com"))
            .andExpect(jsonPath("$.provider").value("GOOGLE"))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.joinedAt").isNotEmpty)
            // 가입 시 지갑이 만들어지므로 0 이 나온다(지갑 없음이 아니다)
            .andExpect(jsonPath("$.coinBalance").value(0))
    }

    @Test
    fun `기록이 없으면 지표가 모두 0 이다`() {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.stats.joinedRoomCount").value(0))
            .andExpect(jsonPath("$.stats.hostedRoomCount").value(0))
            .andExpect(jsonPath("$.stats.hostedSessionCount").value(0))
            .andExpect(jsonPath("$.stats.totalStudentCount").value(0))
    }

    @Test
    fun `내가 만든 방과 참여한 방을 센다`() {
        createRoom("첫 방")
        createRoom("둘째 방")
        joinRoom(createRoom("남의 방"))

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.stats.hostedRoomCount").value(3))
            .andExpect(jsonPath("$.stats.joinedRoomCount").value(1))
            // 아직 종료한 방이 없으니 진행한 세션·누적 학생은 0 이다
            .andExpect(jsonPath("$.stats.hostedSessionCount").value(0))
            .andExpect(jsonPath("$.stats.totalStudentCount").value(0))
    }

    @Test
    fun `진행한 세션과 누적 학생은 종료된 방만 센다`() {
        val roomId = createRoom("끝낸 방")
        joinRoom(roomId)
        endRoom(roomId)
        createRoom("아직 진행 중인 방")

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.stats.hostedRoomCount").value(2))
            .andExpect(jsonPath("$.stats.hostedSessionCount").value(1))
            .andExpect(jsonPath("$.stats.totalStudentCount").value(1))
    }

    @Test
    fun `토큰 없이는 볼 수 없다`() {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized)
    }

    // ---------- helpers ----------

    private fun createRoom(title: String): Long =
        roomService.create(
            userId,
            kr.passmate.room.dto.RoomCreateRequest(title = title),
        ).id

    private fun joinRoom(roomId: Long) {
        mockMvc.perform(
            post("/rooms/{id}/participants", roomId)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"참가-$roomId","avatarId":"cat"}"""),
        ).andExpect(status().isCreated)
    }

    /** 세션을 실제로 돌리는 대신 종료 상태만 만든다 — 여기서 보는 건 집계다. */
    private fun endRoom(roomId: Long) {
        val room = roomRepository.findById(roomId).orElseThrow()
        room.start()
        room.close()
        roomRepository.flush()
        check(room.status == RoomStatus.ENDED)
    }
}
