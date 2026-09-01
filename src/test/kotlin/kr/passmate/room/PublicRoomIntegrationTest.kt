package kr.passmate.room

import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.repository.RoomRepository
import kr.passmate.room.service.RoomService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 홈 인기 방·탐색 목록(FR-054). **게스트도 조회할 수 있다.**
 */
@AutoConfigureMockMvc
@Transactional
class PublicRoomIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var roomRepository: RoomRepository

    private var hostId: Long = 0

    @BeforeEach
    fun setUp() {
        hostId = userService.loginOrRegister(
            AuthProvider.GOOGLE, "pub-host", "host@example.com", "김선생", null,
        ).user.id
    }

    @Test
    fun `공개한 방만 나오고 비공개 방은 빠진다`() {
        createRoom("공개 특강", isPublic = true)
        createRoom("비공개 스터디", isPublic = false)

        publicRooms()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("공개 특강"))
    }

    @Test
    fun `로그인하지 않아도 볼 수 있고 PIN 은 주지 않는다`() {
        createRoom("공개 특강", isPublic = true)

        // 목록은 게스트도 본다 — PIN 을 실으면 공개 목록이 곧 모든 방의 입장 코드 목록이 된다
        publicRooms()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].pin").doesNotExist())
            .andExpect(jsonPath("$.content[0].host.nickname").value("김선생"))
    }

    @Test
    fun `종료된 방은 나오지 않는다`() {
        val ended = createRoom("끝난 방", isPublic = true)
        createRoom("진행 예정", isPublic = true)
        endRoom(ended)

        publicRooms()
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("진행 예정"))
    }

    @Test
    fun `방 이름과 주제로 검색한다`() {
        createRoom("네트워크 기초", isPublic = true, topic = "네트워크")
        createRoom("운영체제 야간반", isPublic = true, topic = "CS 면접")

        publicRooms("q" to "네트워크").andExpect(jsonPath("$.totalElements").value(1))
        publicRooms("q" to "CS").andExpect(jsonPath("$.totalElements").value(1))
        publicRooms("q" to "기초").andExpect(jsonPath("$.totalElements").value(1))
        publicRooms("q" to "없는말").andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `선생님 닉네임으로도 검색된다`() {
        createRoom("아무 제목", isPublic = true)

        publicRooms("q" to "김선생")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("아무 제목"))
    }

    @Test
    fun `운영 중인 방이 인기순 앞에 온다`() {
        val waiting = createRoom("대기 중", isPublic = true)
        val running = createRoom("운영 중", isPublic = true)
        startRoom(running)

        publicRooms()
            .andExpect(jsonPath("$.content[0].title").value("운영 중"))
            .andExpect(jsonPath("$.content[1].title").value("대기 중"))
        // 상태 필터로 한쪽만 볼 수도 있다
        publicRooms("status" to "RUNNING")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(running))
        publicRooms("status" to "WAITING")
            .andExpect(jsonPath("$.content[0].id").value(waiting))
    }

    @Test
    fun `예정순은 시작 시각이 빠른 순이고 시각 없는 방은 뒤로 간다`() {
        createRoom("시각 없음", isPublic = true)
        createRoom("모레", isPublic = true, scheduledAt = LocalDateTime.now().plusDays(2))
        createRoom("내일", isPublic = true, scheduledAt = LocalDateTime.now().plusDays(1))

        publicRooms("sort" to "UPCOMING")
            .andExpect(jsonPath("$.content[0].title").value("내일"))
            .andExpect(jsonPath("$.content[1].title").value("모레"))
            .andExpect(jsonPath("$.content[2].title").value("시각 없음"))
    }

    /** sort 는 Spring Data 의 정렬 파라미터와 이름이 같다 — 우리 값이 정렬 필드로 새면 500 이 난다. */
    @Test
    fun `sort 값을 명시해도 500 이 나지 않는다`() {
        createRoom("공개 특강", isPublic = true)

        publicRooms("sort" to "POPULAR").andExpect(status().isOk)
        publicRooms("sort" to "UPCOMING").andExpect(status().isOk)
    }

    @Test
    fun `오늘 진행 예정만 거른다`() {
        createRoom("오늘", isPublic = true, scheduledAt = LocalDateTime.now().withHour(23).withMinute(0))
        createRoom("내일", isPublic = true, scheduledAt = LocalDateTime.now().plusDays(1))
        createRoom("시각 없음", isPublic = true)

        publicRooms("today" to "true")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("오늘"))
    }

    @Test
    fun `유형으로 거른다`() {
        createRoom("무료 방", isPublic = true)

        publicRooms("type" to "FREE").andExpect(jsonPath("$.totalElements").value(1))
        publicRooms("type" to "PAID").andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `페이지 크기 상한을 넘기면 400 이다`() {
        publicRooms("size" to "500").andExpect(status().isBadRequest)
        publicRooms("page" to "-1").andExpect(status().isBadRequest)
    }

    // ---------- helpers ----------

    private fun publicRooms(vararg params: Pair<String, String>): ResultActions =
        mockMvc.perform(
            get("/rooms/public").apply { params.forEach { (k, v) -> param(k, v) } },
        )

    private fun createRoom(
        title: String,
        isPublic: Boolean,
        topic: String? = null,
        scheduledAt: LocalDateTime? = null,
    ): Long = roomService.create(
        hostId,
        RoomCreateRequest(title = title, topic = topic, isPublic = isPublic, scheduledAt = scheduledAt),
    ).id

    private fun startRoom(roomId: Long) {
        roomRepository.findById(roomId).orElseThrow().start()
        roomRepository.flush()
    }

    private fun endRoom(roomId: Long) {
        val room = roomRepository.findById(roomId).orElseThrow()
        room.start()
        room.close()
        roomRepository.flush()
    }
}
