package kr.passmate.user.service

import kr.passmate.user.domain.User
import kr.passmate.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 다른 기능이 회원을 **읽기만** 할 때 쓰는 창구. 이 빈은 다른 기능을 의존하지 않는다(순환 방지). */
@Service
@Transactional(readOnly = true)
class UserQueryService(
    private val userRepository: UserRepository,
) {

    /** 목록 화면에서 호스트 닉네임을 채울 때 쓴다. 한 번에 조회해 N+1 을 만들지 않는다. */
    fun getNicknames(userIds: Collection<Long>): Map<Long, String> {
        if (userIds.isEmpty()) return emptyMap()
        return userRepository.findAllById(userIds.toSet()).associate { it.id to it.nickname }
    }

    /** 닉네임으로 회원 id 를 찾는다. 공개 방 검색의 "선생님 이름" 조건에 쓴다. */
    fun findIdsByNicknameContaining(keyword: String): List<Long> =
        userRepository.findAllByNicknameContainingIgnoreCase(keyword).map(User::id)
}
