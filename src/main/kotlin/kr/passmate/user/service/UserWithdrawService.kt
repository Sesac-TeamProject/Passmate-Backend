package kr.passmate.user.service

import kr.passmate.coin.service.CoinService
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.room.service.RoomStatsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 탈퇴 (FR-064).
 *
 * 행을 지우지 않는다 — 참여·개설 기록이 이 사용자를 FK 로 가리킨다.
 * 대신 계정을 DELETED 로 내리고 **누구였는지를 지운다**(개인정보 파기).
 */
@Service
class UserWithdrawService(
    private val userService: UserService,
    private val roomStatsService: RoomStatsService,
    private val coinService: CoinService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun withdraw(userId: Long) {
        val user = userService.getActiveUser(userId)

        // 진행 중인 방을 두고 나가면 그 방 학생들이 갈 곳을 잃는다
        if (roomStatsService.hasUnfinishedHostedRoom(userId)) {
            throw BusinessException(
                ErrorCode.CONFLICT,
                "진행 중이거나 시작 전인 방이 있습니다. 방을 먼저 종료해 주세요.",
            )
        }

        // 보유 코인 소멸. 원장에는 소멸 기록이 한 줄 남는다
        coinService.forfeitAll(userId, memo = "회원 탈퇴로 코인 소멸")
        user.withdraw()
        log.info("회원이 탈퇴했다 userId={}", userId)
    }
}
