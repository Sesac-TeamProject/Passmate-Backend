package kr.passmate.moderation.service

import kr.passmate.hostlevel.service.HostGradeQueryService
import kr.passmate.moderation.dto.BlockedUserResponse
import kr.passmate.moderation.dto.BlockedUsersResponse
import kr.passmate.moderation.repository.UserBlockRepository
import kr.passmate.user.service.UserQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 마이페이지 설정의 차단 목록 화면 (FR-067).
 *
 * 닉네임·등급까지 붙이므로 user·hostlevel 에 기댄다. 그래서 이 빈은 **화면 쪽에서만** 쓴다 —
 * room·hostlevel 이 차단 여부를 물을 때는 [UserBlockQueryService] 로 간다.
 */
@Service
@Transactional(readOnly = true)
class BlockedUserQueryService(
    private val userBlockRepository: UserBlockRepository,
    private val userQueryService: UserQueryService,
    private val hostGradeQueryService: HostGradeQueryService,
) {

    fun myBlocks(userId: Long): BlockedUsersResponse {
        val blocks = userBlockRepository.findAllByBlockerUserIdOrderByIdDesc(userId)
        val blockedIds = blocks.map { it.blockedUserId }
        val nicknames = userQueryService.getNicknames(blockedIds)
        val levels = hostGradeQueryService.levelsOrDefault(blockedIds)

        return BlockedUsersResponse(
            totalCount = blocks.size,
            blocks = blocks.map {
                BlockedUserResponse(
                    userId = it.blockedUserId,
                    nickname = nicknames[it.blockedUserId] ?: "",
                    level = levels.getValue(it.blockedUserId),
                    blockedAt = it.createdAt,
                )
            },
        )
    }
}
