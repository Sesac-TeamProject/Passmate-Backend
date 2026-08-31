package kr.passmate.auth.dto

import kr.passmate.user.domain.User

data class UserSummary(
    val id: Long,
    val nickname: String,
    val email: String?,
    val profileImageUrl: String?,
    val defaultAvatarId: String?,
    val isAdmin: Boolean,
) {
    companion object {
        fun from(user: User) = UserSummary(
            id = user.id,
            nickname = user.nickname,
            email = user.email,
            profileImageUrl = user.profileImageUrl,
            defaultAvatarId = user.defaultAvatarId,
            isAdmin = user.isAdmin,
        )
    }
}
