package kr.passmate.hostlevel.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.hostlevel.config.HostLevelProperties
import kr.passmate.hostlevel.dto.HostProfileResponse
import kr.passmate.room.service.PublicRoomQueryService
import kr.passmate.user.domain.User
import kr.passmate.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 선생님 공개 프로필 (FR-066, M-10 · M-11).
 *
 * 로그인 없이도 볼 수 있는 화면이라 담는 값을 좁게 잡는다.
 */
@Service
@Transactional(readOnly = true)
class HostProfileQueryService(
    private val properties: HostLevelProperties,
    private val userService: UserService,
    private val publicRoomQueryService: PublicRoomQueryService,
    private val hostGradeQueryService: HostGradeQueryService,
    private val badgeQueryService: BadgeQueryService,
) {

    fun getProfile(userId: Long): HostProfileResponse {
        val user = activeUser(userId)
        val profile = hostGradeQueryService.findProfile(userId)
        val level = profile?.level ?: properties.lowest.level
        val badges = badgeQueryService.achievedBadges(userId)

        return HostProfileResponse(
            userId = user.id,
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            defaultAvatarId = user.defaultAvatarId,
            activeSince = user.createdAt,
            level = level,
            levelName = properties.ruleOf(level).name,
            avgRating = profile?.avgRating?.toDouble(),
            ratingCount = profile?.ratingCount ?: 0,
            roomsHosted = profile?.roomsHosted ?: 0,
            totalStudents = profile?.totalStudents ?: 0,
            badgeCount = badges.size,
            badges = badges,
            openRooms = publicRoomQueryService.openRoomsOfHost(userId),
        )
    }

    /**
     * 탈퇴한 계정은 프로필이 남아 있어도 보여주지 않는다 —
     * 닉네임이 "탈퇴한 사용자"로 익명화돼 있어 보여 줄 것도 없다.
     */
    private fun activeUser(userId: Long): User =
        runCatching { userService.getActiveUser(userId) }
            .getOrElse { throw BusinessException(ErrorCode.USER_NOT_FOUND) }
}
