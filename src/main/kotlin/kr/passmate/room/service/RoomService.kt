package kr.passmate.room.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.room.domain.Room
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val pinService: PinService,
) {

    /**
     * 방을 개설하고 PIN 을 발급한다. 문제 세트는 없어도 된다 —
     * 화면 흐름상 세트가 없으면 개설 후 에디터에서 만든다(피그마 v6 W-02).
     */
    @Transactional
    fun create(hostUserId: Long, request: RoomCreateRequest): Room {
        verifySupportedType(request.type)
        return roomRepository.save(
            Room(
                hostUserId = hostUserId,
                pin = pinService.issue(),
                type = request.type,
                title = request.title,
                description = request.description,
                topic = request.topic,
                questionSetId = request.questionSetId,
                fee = request.fee,
                maxParticipants = request.maxParticipants,
                isPublic = request.isPublic,
                scheduledAt = request.scheduledAt,
            ),
        )
    }

    @Transactional
    fun update(roomId: Long, hostUserId: Long, request: RoomUpdateRequest): Room {
        val room = getOwnedRoom(roomId, hostUserId)
        room.update(
            title = request.title,
            description = request.description,
            topic = request.topic,
            questionSetId = request.questionSetId,
            maxParticipants = request.maxParticipants,
            isPublic = request.isPublic,
            scheduledAt = request.scheduledAt,
        )
        return room
    }

    /**
     * 방을 닫는다. 시작 전이면 취소, 진행 중이었으면 종료. 어느 쪽이든 PIN 이 풀린다.
     *
     * TODO(coin): 유료 방이면 참가비 코인을 환급한다. coin 기능 구현 시 CoinService 호출을 붙인다.
     */
    @Transactional
    fun close(roomId: Long, hostUserId: Long): Room {
        val room = getOwnedRoom(roomId, hostUserId)
        room.close()
        return room
    }

    private fun getOwnedRoom(roomId: Long, hostUserId: Long): Room {
        val room = roomRepository.findById(roomId)
            .orElseThrow { BusinessException(ErrorCode.ROOM_NOT_FOUND) }
        room.verifyHost(hostUserId)
        return room
    }

    /**
     * 유료·브랜디드 방은 코인 차감과 호스트 등급 판정이 필요한데 그 기능이 아직 없다.
     * 지금 열어두면 참가비를 받지 않고 입장시키게 되므로 명시적으로 막는다.
     */
    private fun verifySupportedType(type: RoomType) {
        if (type != RoomType.FREE) {
            throw BusinessException(
                ErrorCode.UNSUPPORTED_ROOM_TYPE,
                "유료·브랜디드 방은 코인 결제 기능 구현 후 열립니다. 지금은 무료 방만 만들 수 있습니다.",
            )
        }
    }

    companion object {
        val ACTIVE_STATUSES = listOf(RoomStatus.WAITING, RoomStatus.RUNNING)
    }
}
