package kr.passmate.user.service

import kr.passmate.coin.service.CoinWalletService
import kr.passmate.room.service.RoomStatsService
import kr.passmate.user.dto.MyProfileResponse
import kr.passmate.user.dto.UserProfileUpdateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 마이페이지 조회. 프로필은 user 가 갖고 있지만 지표·코인은 남의 기능 것이라
 * **각 기능의 Service 를 통해서만** 읽는다(다른 기능의 Repository 를 직접 부르지 않는다).
 */
@Service
@Transactional(readOnly = true)
class UserProfileService(
    private val userService: UserService,
    private val roomStatsService: RoomStatsService,
    private val coinWalletService: CoinWalletService,
) {

    /** 닉네임·프로필 이미지·기본 캐릭터를 고치고 바뀐 프로필을 그대로 돌려준다. */
    @Transactional
    fun updateMyProfile(userId: Long, request: UserProfileUpdateRequest): MyProfileResponse {
        userService.updateProfile(
            userId = userId,
            nickname = request.nickname,
            profileImageUrl = request.profileImageUrl,
            defaultAvatarId = request.defaultAvatarId,
        )
        return getMyProfile(userId)
    }

    fun getMyProfile(userId: Long): MyProfileResponse {
        val user = userService.getActiveUser(userId)
        return MyProfileResponse.of(
            user = user,
            stats = roomStatsService.getUserRoomStats(userId),
            coinBalance = coinWalletService.getBalance(userId),
        )
    }
}
