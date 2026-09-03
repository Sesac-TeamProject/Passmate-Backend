package kr.passmate.room.service

import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.hostlevel.service.HostGradeQueryService
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
    private val hostGradeQueryService: HostGradeQueryService,
    private val policyProperties: PolicyProperties,
) {

    /**
     * 방을 개설하고 PIN 을 발급한다. 문제 세트는 없어도 된다 —
     * 화면 흐름상 세트가 없으면 개설 후 에디터에서 만든다(피그마 v6 W-02).
     */
    @Transactional
    fun create(hostUserId: Long, request: RoomCreateRequest): Room {
        verifySupportedType(request.type)
        verifyFee(request.type, request.fee)
        verifyHostLevel(request.type, hostUserId)
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
     * 브랜디드 방은 기업 위탁 계약·정산이 따로 붙는데 그 기능이 아직 없다.
     * 지금 열어두면 계약 없이 브랜디드로 표시되는 방이 생기므로 명시적으로 막는다.
     */
    private fun verifySupportedType(type: RoomType) {
        if (type == RoomType.BRANDED) {
            throw BusinessException(
                ErrorCode.UNSUPPORTED_ROOM_TYPE,
                "브랜디드 방은 기업 위탁 기능 구현 후 열립니다.",
            )
        }
    }

    /**
     * 참가비는 유료 방에만, 그리고 정책 범위 안에서만 붙는다.
     *
     * 무료 방에 참가비가 붙는 것도 막는다 — 화면은 무료라고 알리는데 값이 남아 있으면
     * 나중에 유형만 바꿔도 조용히 돈을 걷게 된다.
     */
    private fun verifyFee(type: RoomType, fee: Int?) {
        if (type == RoomType.FREE) {
            if (fee != null) {
                throw BusinessException(ErrorCode.INVALID_INPUT, "무료 방에는 참가비를 붙일 수 없습니다.")
            }
            return
        }
        if (fee == null) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "유료 방은 참가비가 필요합니다.")
        }
        val min = policyProperties.entryFeeMin
        val max = policyProperties.entryFeeMax
        if (fee !in min..max) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "참가비는 $min~$max 코인 사이여야 합니다.")
        }
    }

    /**
     * 참가비를 받으려면 Lv.3 이상이어야 한다(FR-046).
     * 등급 이력이 없는 호스트는 Lv.1 로 본다 — 없다고 통과시키면 게이트가 없는 것과 같다.
     */
    private fun verifyHostLevel(type: RoomType, hostUserId: Long) {
        if (type == RoomType.FREE) return
        val level = hostGradeQueryService.levelsOrDefault(listOf(hostUserId))[hostUserId] ?: LOWEST_LEVEL
        if (level < PAID_ROOM_MIN_LEVEL) {
            throw BusinessException(ErrorCode.HOST_LEVEL_REQUIRED)
        }
    }

    companion object {
        val ACTIVE_STATUSES = listOf(RoomStatus.WAITING, RoomStatus.RUNNING)

        /** 유료 방 개설에 필요한 최소 등급 (FR-046) */
        private const val PAID_ROOM_MIN_LEVEL = 3
        private const val LOWEST_LEVEL = 1
    }
}
