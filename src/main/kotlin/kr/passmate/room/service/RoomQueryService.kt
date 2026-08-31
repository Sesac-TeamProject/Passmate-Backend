package kr.passmate.room.service

import kr.passmate.common.config.ClientProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.util.QrCodeGenerator
import kr.passmate.room.domain.Room
import kr.passmate.room.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RoomQueryService(
    private val roomRepository: RoomRepository,
    private val qrCodeGenerator: QrCodeGenerator,
    private val clientProperties: ClientProperties,
) {

    fun getRoom(roomId: Long): Room =
        roomRepository.findById(roomId)
            .orElseThrow { BusinessException(ErrorCode.ROOM_NOT_FOUND) }

    /** PIN 은 활성 방 사이에서만 유일하다. 종료된 방은 조회되지 않는다. */
    fun getActiveRoomByPin(pin: String): Room =
        roomRepository.findByPinAndStatusIn(pin, PinService.ACTIVE_STATUSES)
            ?: throw BusinessException(ErrorCode.ROOM_NOT_FOUND, "해당 PIN 의 방이 없습니다.")

    /** 입장 링크를 담은 QR PNG. 호스트만 받을 수 있다. */
    fun getQrPng(roomId: Long, hostUserId: Long): ByteArray {
        val room = getRoom(roomId)
        room.verifyHost(hostUserId)
        return qrCodeGenerator.toPngBytes(clientProperties.joinUrl(room.pin))
    }
}
