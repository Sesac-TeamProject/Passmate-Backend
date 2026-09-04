package kr.passmate.room.service

import kr.passmate.common.event.ParticipantJoinedEvent
import kr.passmate.common.event.ParticipantLeftEvent
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.common.security.UserPrincipal
import kr.passmate.room.domain.Participant
import kr.passmate.room.domain.ParticipantStatus
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.repository.ParticipantRepository
import kr.passmate.room.repository.RoomRepository
import kr.passmate.user.service.UserService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 입장 결과. 게스트면 두 가지 토큰을 함께 준다.
 * - `accessToken` — 세션 API 를 부를 때 쓰는 JWT (액세스 토큰과 같은 수명)
 * - `guestToken` — 나중에 "가입하고 기록 저장하기"로 기록을 계정에 옮길 때 제출하는 값(7일 보관, FR-030)
 */
data class JoinResult(
    val participant: Participant,
    val accessToken: String?,
    val guestToken: String?,
)

@Service
class ParticipantService(
    private val roomRepository: RoomRepository,
    private val participantRepository: ParticipantRepository,
    private val userService: UserService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val entryPaymentService: EntryPaymentService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    /**
     * 방에 입장한다. 회원은 계정에 연동되고, 게스트는 가입 없이 들어온다.
     *
     * 정원 확인과 인원 증가 사이에 다른 입장이 끼어들지 못하도록 방 행에 비관적 락을 건다.
     */
    @Transactional
    fun join(roomId: Long, userId: Long?, request: JoinRoomRequest): JoinResult {
        val room = roomRepository.findByIdForUpdate(roomId)
            ?: throw BusinessException(ErrorCode.ROOM_NOT_FOUND)

        room.verifyJoinable()
        verifyGuestAllowed(room, userId)
        // 호스트는 자기 방에 참가자로 들어올 수 없다 — 프론트 버튼을 가려도 API 직접 호출로 뚫리면 안 되므로 서버에서 막는다
        if (userId != null && userId == room.hostUserId) {
            throw BusinessException(ErrorCode.HOST_CANNOT_JOIN)
        }

        val nickname = request.nickname.trim()
        if (participantRepository.existsByRoomIdAndNickname(roomId, nickname)) {
            throw BusinessException(ErrorCode.NICKNAME_DUPLICATED)
        }

        userId?.let { verifyNotAlreadyJoined(roomId, it) }

        val guestToken = if (userId == null) UUID.randomUUID().toString().replace("-", "") else null
        val participant = participantRepository.save(
            Participant(
                roomId = roomId,
                userId = userId,
                nickname = nickname,
                avatarId = resolveAvatarId(userId, request.avatarId),
                guestToken = guestToken,
                deviceKey = request.deviceKey,
            ),
        )
        room.increaseParticipantCount()

        // 유료 방은 살아 있는 참가비 결제가 있어야 들어온다(FR-051).
        // 게이트는 서버에만 있다 — 결제 화면을 건너뛰고 이 API 를 바로 불러도 막힌다
        entryPaymentService.consumeForJoin(room, userId, participant.id)

        // 결제 게이트까지 통과한 뒤에만 알린다 — 입장이 무산된 사람이 명단에 떠서는 안 된다
        applicationEventPublisher.publishEvent(participant.toJoinedEvent())

        return JoinResult(
            participant = participant,
            // 회원은 이미 자기 액세스 토큰이 있으므로 새로 주지 않는다
            accessToken = guestToken?.let { jwtTokenProvider.issueGuestToken(participant.id, roomId) },
            guestToken = guestToken,
        )
    }

    /**
     * 방에서 나간다. 회원은 계정으로, 게스트는 토큰에 담긴 참가자 id 로 자기 자신을 찾는다.
     *
     * TODO(coin): 유료 방은 세션 시작 전 퇴장이면 참가비를 환급한다.
     */
    @Transactional
    fun leave(roomId: Long, principal: AuthPrincipal) {
        val participant = when (principal) {
            is UserPrincipal -> getJoinedParticipantOfUser(roomId, principal.userId)
            is GuestPrincipal -> getParticipant(principal.participantId)
        }
        verifyBelongsTo(roomId, participant)
        participant.leave()
        decreaseCount(roomId)
        applicationEventPublisher.publishEvent(participant.toLeftEvent())
    }

    /**
     * 호스트가 참가자를 내보낸다.
     *
     * TODO(coin): 유료 방 세션 시작 전이면 참가비를 환급한다.
     */
    @Transactional
    fun kick(roomId: Long, participantId: Long, hostUserId: Long) {
        val room = roomRepository.findById(roomId)
            .orElseThrow { BusinessException(ErrorCode.ROOM_NOT_FOUND) }
        room.verifyHost(hostUserId)

        val participant = getParticipant(participantId)
        verifyBelongsTo(roomId, participant)
        participant.kick()
        decreaseCount(roomId)
        applicationEventPublisher.publishEvent(participant.toLeftEvent())
    }

    @Transactional(readOnly = true)
    fun getParticipant(participantId: Long): Participant =
        participantRepository.findById(participantId)
            .orElseThrow { BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND) }

    @Transactional(readOnly = true)
    fun getJoinedParticipantOfUser(roomId: Long, userId: Long): Participant =
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            ?: throw BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND, "이 방에 입장한 기록이 없습니다.")

    private fun decreaseCount(roomId: Long) {
        roomRepository.findByIdForUpdate(roomId)?.decreaseParticipantCount()
    }

    private fun Participant.toJoinedEvent() =
        ParticipantJoinedEvent(roomId, id, nickname, avatarId, isGuest, joinedAt)

    private fun Participant.toLeftEvent() =
        ParticipantLeftEvent(roomId, id, nickname, avatarId, isGuest, joinedAt)

    private fun verifyBelongsTo(roomId: Long, participant: Participant) {
        if (participant.roomId != roomId) {
            throw BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND)
        }
    }

    private fun verifyNotAlreadyJoined(roomId: Long, userId: Long) {
        val joined = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
        if (joined != null) throw BusinessException(ErrorCode.ALREADY_JOINED)
    }

    /** 유료 방은 회원 전용이다. 게스트는 로그인·가입으로 유도한다(FR-046). */
    private fun verifyGuestAllowed(room: Room, userId: Long?) {
        if (userId == null && room.type != RoomType.FREE) {
            throw BusinessException(ErrorCode.GUEST_NOT_ALLOWED)
        }
    }

    /** 회원이 캐릭터를 고르지 않았으면 마이페이지의 기본 캐릭터를 쓴다. */
    private fun resolveAvatarId(userId: Long?, requested: String?): String {
        requested?.takeIf { it.isNotBlank() }?.let { return it }
        userId?.let { return userService.getActiveUser(it).defaultAvatarId ?: DEFAULT_AVATAR_ID }
        return DEFAULT_AVATAR_ID
    }

    companion object {
        const val DEFAULT_AVATAR_ID = "default"
    }
}
