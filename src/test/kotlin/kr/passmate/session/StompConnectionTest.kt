package kr.passmate.session

import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.service.ParticipantService
import kr.passmate.room.service.RoomService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.concurrent.TimeUnit

/**
 * STOMP 연결·구독 인가가 실제로 도는지 확인한다.
 * 진짜 WebSocket 핸드셰이크를 거치므로 RANDOM_PORT 로 서버를 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompConnectionTest : IntegrationTestSupport() {

    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private fun client() = WebSocketStompClient(StandardWebSocketClient()).apply {
        messageConverter = MappingJackson2MessageConverter()
    }

    private fun connect(token: String?): StompSession {
        val headers = StompHeaders()
        token?.let { headers.add("Authorization", "Bearer $it") }
        return client()
            .connectAsync("ws://localhost:$port/ws", org.springframework.web.socket.WebSocketHttpHeaders(), headers, object : StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS)
    }

    @Test
    fun `토큰 없이 연결하면 거부된다`() {
        assertThatThrownBy { connect(null) }
            .hasMessageContaining("")
    }

    @Test
    fun `위조된 토큰으로 연결하면 거부된다`() {
        assertThatThrownBy { connect("not-a-jwt") }
            .hasMessageContaining("")
    }

    @Test
    fun `호스트는 방 토픽과 호스트 토픽을 모두 구독한다`() {
        val host = member("stomp-host")
        val room = roomService.create(host, RoomCreateRequest(title = "실시간 테스트", type = RoomType.FREE))
        val session = connect(jwtTokenProvider.issue(host, false).accessToken)

        assertThat(session.isConnected).isTrue()
        session.subscribe("/topic/rooms/${room.id}", handler())
        session.subscribe("/topic/rooms/${room.id}/host", handler())
        session.disconnect()
    }

    @Test
    fun `참가자는 방 토픽만 구독할 수 있고 호스트 토픽은 막힌다`() {
        val host = member("stomp-host2")
        val student = member("stomp-student")
        val room = roomService.create(host, RoomCreateRequest(title = "실시간 테스트", type = RoomType.FREE))
        participantService.join(room.id, student, JoinRoomRequest(nickname = "학생"))

        val session = connect(jwtTokenProvider.issue(student, false).accessToken)
        session.subscribe("/topic/rooms/${room.id}", handler())

        // 호스트 토픽 구독은 ERROR 프레임을 유발해 세션이 끊긴다
        session.subscribe("/topic/rooms/${room.id}/host", handler())
        Thread.sleep(300)
        assertThat(session.isConnected).isFalse()
    }

    @Test
    fun `남의 방 토픽은 구독할 수 없다`() {
        val host = member("stomp-host3")
        val outsider = member("stomp-outsider")
        val room = roomService.create(host, RoomCreateRequest(title = "실시간 테스트", type = RoomType.FREE))

        val session = connect(jwtTokenProvider.issue(outsider, false).accessToken)
        session.subscribe("/topic/rooms/${room.id}", handler())
        Thread.sleep(300)
        assertThat(session.isConnected).isFalse()
    }

    @Test
    fun `게스트 토큰은 자기가 입장한 방만 구독한다`() {
        val host = member("stomp-host4")
        val room = roomService.create(host, RoomCreateRequest(title = "실시간 테스트", type = RoomType.FREE))
        val other = roomService.create(host, RoomCreateRequest(title = "다른 방", type = RoomType.FREE))
        val joined = participantService.join(room.id, null, JoinRoomRequest(nickname = "게스트"))

        val session = connect(joined.accessToken)
        session.subscribe("/topic/rooms/${room.id}", handler())
        assertThat(session.isConnected).isTrue()

        session.subscribe("/topic/rooms/${other.id}", handler())
        Thread.sleep(300)
        assertThat(session.isConnected).isFalse()
    }

    private fun handler() = object : StompSessionHandlerAdapter() {}

    private fun member(key: String): Long = userService.loginOrRegister(
        AuthProvider.GOOGLE, key, "$key@example.com", key, null,
    ).user.id
}
