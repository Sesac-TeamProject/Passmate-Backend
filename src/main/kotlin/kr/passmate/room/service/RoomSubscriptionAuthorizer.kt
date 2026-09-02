package kr.passmate.room.service

import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.StompSubscriptionAuthorizer
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 방 토픽 구독 인가.
 *
 * - `/topic/rooms/{roomId}`        → 그 방의 참가자 또는 호스트
 * - `/topic/rooms/{roomId}/host`   → 호스트만 (제출 현황·정답률이 흐르는 채널)
 * - `/user/queue/...`              → 인증된 주체 (Spring 이 세션별로 격리한다)
 * - 그 외                           → 거부
 *
 * 모르는 목적지는 무조건 거부한다. 토픽을 새로 만들 때 여기에 규칙을 같이 넣지 않으면
 * 아무도 구독하지 못하고 바로 드러나므로, 조용히 열려 있는 것보다 안전하다.
 */
@Component
class RoomSubscriptionAuthorizer(
    private val roomRepository: RoomRepository,
    private val participantQueryService: ParticipantQueryService,
) : StompSubscriptionAuthorizer {

    @Transactional(readOnly = true)
    override fun canSubscribe(principal: AuthPrincipal, destination: String): Boolean {
        if (destination.startsWith(USER_QUEUE_PREFIX)) return true

        val match = ROOM_TOPIC.matchEntire(destination) ?: return false
        val roomId = match.groupValues[1].toLongOrNull() ?: return false
        val hostOnly = match.groupValues[2] == "/host"

        return when (principal) {
            is UserPrincipal -> {
                val isHost = roomRepository.findById(roomId)
                    .map { it.hostUserId == principal.userId }
                    .orElse(false)
                if (hostOnly) isHost else isHost || participantQueryService.isJoined(roomId, principal.userId)
            }

            // 게스트 토큰은 입장한 방 하나에만 유효하고, 호스트 채널은 볼 수 없다
            is GuestPrincipal -> !hostOnly && principal.roomId == roomId
        }
    }

    companion object {
        private const val USER_QUEUE_PREFIX = "/user/queue/"
        // raw string 끝에 $ 가 오면 Kotlin 이 템플릿으로 읽어 깨진다. 일반 문자열로 이스케이프한다
        private val ROOM_TOPIC = Regex("^/topic/rooms/(\\d+)(/host)?\$")
    }
}
