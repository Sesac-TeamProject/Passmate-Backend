package kr.passmate.rating.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.rating.domain.RoomRating
import kr.passmate.rating.dto.RoomRatingRequest
import kr.passmate.rating.repository.RoomRatingRepository
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.service.AnswerQueryService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 세션 평가 제출 (FR-042 · FR-043, M-06 v2).
 *
 * 무료 방은 게스트도 평가할 수 있어서 [AuthPrincipal] 을 그대로 받는다.
 */
@Service
class RoomRatingService(
    private val roomQueryService: RoomQueryService,
    private val answerQueryService: AnswerQueryService,
    private val roomRatingQueryService: RoomRatingQueryService,
    private val roomRatingRepository: RoomRatingRepository,
) {

    /**
     * 자격은 조회 화면과 **같은 판정**([RoomRatingQueryService.availability])으로 본다 —
     * 두 곳에 조건을 따로 적으면 "평가할 수 있다"고 띄워 놓고 제출은 막는 일이 생긴다.
     */
    @Transactional
    fun submit(roomId: Long, principal: AuthPrincipal, request: RoomRatingRequest): RoomRating {
        val room = roomQueryService.getRoom(roomId)
        val participantId = answerQueryService.resolveParticipantId(roomId, principal)
        val hasSubmitted = answerQueryService.listByParticipant(participantId).isNotEmpty()

        val availability = roomRatingQueryService.availability(room, participantId, hasSubmitted)
        availability.blockedReason?.let { throw BusinessException(it.errorCode) }

        val rating = RoomRating(
            roomId = room.id,
            participantId = participantId,
            hostUserId = room.hostUserId,
            stars = request.stars,
            tags = request.tags?.distinct()?.takeIf { it.isNotEmpty() },
            comment = request.comment?.trim()?.takeIf { it.isNotBlank() },
        )

        return try {
            roomRatingRepository.saveAndFlush(rating)
        } catch (e: DataIntegrityViolationException) {
            // uk_room_rating — 같은 사람이 제출 버튼을 두 번 눌러 위 검사를 나란히 통과한 경우.
            // 여기서 안 잡으면 500 이 나가서 "이미 평가함"이 서버 장애처럼 보인다
            throw BusinessException(ErrorCode.ALREADY_RATED, cause = e)
        }
    }
}
