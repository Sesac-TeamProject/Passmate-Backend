package kr.passmate.user.repository

import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.domain.User
import kr.passmate.user.domain.UserStatus
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {

    /** 공개 방 검색의 "선생님 이름" 조건. 탈퇴 계정은 방도 함께 사라지므로 따로 거르지 않는다. */
    fun findAllByNicknameContainingIgnoreCase(nickname: String): List<User>

    /** uk_user_provider (provider, provider_id) 로 조회한다. */
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?

    /** 탈퇴 여부만 본다. 인증 필터가 매 요청 부르므로 행을 통째로 읽지 않는다. */
    fun existsByIdAndStatus(id: Long, status: UserStatus): Boolean
}
