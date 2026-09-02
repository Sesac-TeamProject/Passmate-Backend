package kr.passmate.moderation.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.moderation.domain.UserBlock
import kr.passmate.moderation.repository.UserBlockRepository
import kr.passmate.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 호스트 차단·해제 (FR-067).
 *
 * 두 동작 모두 **멱등**이다 — 목록 화면에서 토글을 두 번 눌러도 결과가 같아야 하고,
 * "이미 차단했습니다" 오류는 사용자가 할 수 있는 일이 없는 오류다.
 */
@Service
class UserBlockService(
    private val userBlockRepository: UserBlockRepository,
    private val userService: UserService,
) {

    @Transactional
    fun block(blockerUserId: Long, blockedUserId: Long) {
        if (blockerUserId == blockedUserId) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "자기 자신은 차단할 수 없습니다.")
        }
        // 없는 계정을 차단해 두면 목록에 유령 줄이 남는다
        userService.getActiveUser(blockedUserId)

        if (userBlockRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)) return
        userBlockRepository.save(UserBlock(blockerUserId = blockerUserId, blockedUserId = blockedUserId))
    }

    @Transactional
    fun unblock(blockerUserId: Long, blockedUserId: Long) {
        userBlockRepository.deleteByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
    }
}
