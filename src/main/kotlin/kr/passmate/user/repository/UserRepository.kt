package kr.passmate.user.repository

import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {

    /** uk_user_provider (provider, provider_id) 로 조회한다. */
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
