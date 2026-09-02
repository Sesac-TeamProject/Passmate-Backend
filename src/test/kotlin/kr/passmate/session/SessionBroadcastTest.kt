package kr.passmate.session

import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.service.RoomService
import kr.passmate.session.service.SessionService
import kr.passmate.voicehint.service.VoiceHintService
import kr.passmate.support.FakeStorageConfig
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 실제 WebSocket 으로 흘러나가는 페이로드를 받아 검증한다.
 * 특히 **QUESTION_STARTED 에 정답이 실리지 않는지**는 여기서만 확인할 수 있다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeStorageConfig::class)
class SessionBroadcastTest : IntegrationTestSupport() {

    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var questionSetService: QuestionSetService
    @Autowired private lateinit var sessionService: SessionService
    @Autowired private lateinit var voiceHintService: VoiceHintService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    @Test
    fun `QUESTION_STARTED 에는 정답과 해설이 실리지 않고 QUESTION_ENDED 에서 나온다`() {
        val hostId = userService.loginOrRegister(
            AuthProvider.GOOGLE, "bc-host-${System.nanoTime()}", null, "호스트", null,
        ).user.id

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("방송 테스트"))
        questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(
                type = QuestionType.MCQ,
                content = "404 는?",
                choices = listOf("성공", "찾을 수 없음"),
                answer = "찾을 수 없음",
                explanation = "Not Found 입니다",
                timeLimitSec = 30,
                points = 100,
            ),
        )
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "방송", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "방송", questionSetId = set.id))

        val received = LinkedBlockingQueue<Map<String, Any?>>()
        val session = subscribeRoom(room.id, hostId, received)

        sessionService.start(room.id, hostId)
        val started = drainUntil(received, "QUESTION_STARTED")

        @Suppress("UNCHECKED_CAST")
        val startPayload = started["payload"] as Map<String, Any?>
        assertThat(startPayload).containsKeys("content", "choices", "endsAt")
        // 핵심: 정답·해설이 없어야 한다
        assertThat(startPayload).doesNotContainKeys("answer", "explanation")
        assertThat(startPayload.values.filterIsInstance<String>()).doesNotContain("Not Found 입니다")

        sessionService.endCurrentQuestion(room.id, hostId)
        val ended = drainUntil(received, "QUESTION_ENDED")

        @Suppress("UNCHECKED_CAST")
        val endPayload = ended["payload"] as Map<String, Any?>
        // 마감 시점에 비로소 정답이 나간다
        assertThat(endPayload["answer"]).isEqualTo("찾을 수 없음")
        assertThat(endPayload["explanation"]).isEqualTo("Not Found 입니다")
        assertThat(endPayload).containsKey("distribution")

        session.disconnect()
    }

    @Test
    fun `화면을 잠그면 방 전체에 SCREEN_LOCKED 가 나간다`() {
        val hostId = userService.loginOrRegister(
            AuthProvider.GOOGLE, "bc-lock-${System.nanoTime()}", null, "호스트", null,
        ).user.id

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("잠금 테스트"))
        questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(
                type = QuestionType.MCQ,
                content = "404 는?",
                choices = listOf("성공", "찾을 수 없음"),
                answer = "찾을 수 없음",
                timeLimitSec = 30,
                points = 100,
            ),
        )
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "잠금", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "잠금", questionSetId = set.id))
        sessionService.start(room.id, hostId)

        val received = LinkedBlockingQueue<Map<String, Any?>>()
        val session = subscribeRoom(room.id, hostId, received)

        sessionService.lockScreen(room.id, hostId, true)
        val locked = drainUntil(received, "SCREEN_LOCKED")

        @Suppress("UNCHECKED_CAST")
        val lockedPayload = locked["payload"] as Map<String, Any?>
        assertThat(lockedPayload["locked"]).isEqualTo(true)

        sessionService.lockScreen(room.id, hostId, false)

        @Suppress("UNCHECKED_CAST")
        val unlockedPayload = drainUntil(received, "SCREEN_LOCKED")["payload"] as Map<String, Any?>
        assertThat(unlockedPayload["locked"]).isEqualTo(false)

        session.disconnect()
    }

    @Test
    fun `음성 힌트를 보내면 방 전체가 재생 주소를 받는다`() {
        val hostId = userService.loginOrRegister(
            AuthProvider.GOOGLE, "bc-hint-${System.nanoTime()}", null, "호스트", null,
        ).user.id

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("힌트 방송"))
        questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(
                type = QuestionType.MCQ,
                content = "404 는?",
                choices = listOf("성공", "찾을 수 없음"),
                answer = "찾을 수 없음",
                timeLimitSec = 30,
                points = 100,
            ),
        )
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "힌트 방송", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "힌트 방송", questionSetId = set.id))
        sessionService.start(room.id, hostId)

        val received = LinkedBlockingQueue<Map<String, Any?>>()
        val session = subscribeRoom(room.id, hostId, received)

        voiceHintService.publish(
            roomId = room.id,
            hostUserId = hostId,
            file = MockMultipartFile("file", "hint.webm", "audio/webm", byteArrayOf(1, 2, 3)),
            durationMs = 1800,
        )

        @Suppress("UNCHECKED_CAST")
        val payload = drainUntil(received, "HINT_PUBLISHED")["payload"] as Map<String, Any?>
        // 학생 화면이 3초 안에 자동 재생하려면 주소가 이벤트에 실려 있어야 한다
        assertThat(payload["audioUrl"].toString()).startsWith("https://fake-storage.test/rooms/${room.id}/hints/")
        assertThat(payload["durationMs"]).isEqualTo(1800)
        assertThat(payload).containsKeys("hintId", "sessionQuestionId", "questionId", "publishedAt")

        session.disconnect()
    }

    /** 방 토픽을 구독하고 들어오는 이벤트를 [received] 에 쌓는다. */
    private fun subscribeRoom(
        roomId: Long,
        userId: Long,
        received: LinkedBlockingQueue<Map<String, Any?>>,
    ): StompSession {
        val client = WebSocketStompClient(StandardWebSocketClient()).apply {
            messageConverter = MappingJackson2MessageConverter()
        }
        val headers = StompHeaders().apply {
            add("Authorization", "Bearer ${jwtTokenProvider.issue(userId, false).accessToken}")
        }
        val session = client
            .connectAsync("ws://localhost:$port/ws", WebSocketHttpHeaders(), headers, object : StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS)

        session.subscribe(
            "/topic/rooms/$roomId",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                @Suppress("UNCHECKED_CAST")
                override fun handleFrame(headers: StompHeaders, payload: Any?) {
                    received.add(payload as Map<String, Any?>)
                }
            },
        )
        // 구독이 브로커에 등록될 틈을 준다 — 바로 이벤트를 쏘면 놓친다
        Thread.sleep(300)
        return session
    }

    private fun drainUntil(queue: LinkedBlockingQueue<Map<String, Any?>>, type: String): Map<String, Any?> {
        repeat(30) {
            val event = queue.poll(1, TimeUnit.SECONDS) ?: return@repeat
            if (event["type"] == type) return event
        }
        error("$type 이벤트를 받지 못했습니다.")
    }
}
