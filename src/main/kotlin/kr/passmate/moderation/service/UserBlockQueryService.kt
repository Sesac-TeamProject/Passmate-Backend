package kr.passmate.moderation.service

import kr.passmate.moderation.repository.UserBlockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * "이 사람 차단했나"만 답하는 창구 (FR-067).
 *
 * **일부러 아무 기능에도 기대지 않는다.** 차단을 물어보는 쪽은 room·hostlevel 인데,
 * 목록 화면용 닉네임·등급까지 여기서 붙이면 moderation → hostlevel → moderation 이 되어
 * 순환 참조가 된다. 화면용 조립은 [BlockedUserQueryService] 가 따로 맡는다.
 */
@Service
@Transactional(readOnly = true)
class UserBlockQueryService(
    private val userBlockRepository: UserBlockRepository,
) {

    /** 내가 차단한 사람 id. 공개 방 목록이 걸러낼 때 쓴다. */
    fun blockedIdsOf(userId: Long): List<Long> = userBlockRepository.findBlockedUserIds(userId)

    fun isBlocked(blockerUserId: Long, blockedUserId: Long): Boolean =
        userBlockRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)

    /** 보는 사람이 없으면(비로그인) 가릴 것도 없다. */
    fun blockedIdsOfOrEmpty(userId: Long?): List<Long> =
        userId?.let(::blockedIdsOf) ?: emptyList()
}
