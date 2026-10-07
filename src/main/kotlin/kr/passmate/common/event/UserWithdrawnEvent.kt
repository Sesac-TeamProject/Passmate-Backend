package kr.passmate.common.event

/**
 * 회원이 탈퇴했다(커밋 후 처리용).
 *
 * Apple 로그인 회원이면 Apple ID 연결을 끊어야 한다(App Store 심사 지침 5.1.1(v)). 외부 호출이라
 * 탈퇴 트랜잭션 안에서 하지 않고, 커밋된 뒤 auth 기능이 이 이벤트를 받아 처리한다.
 * 토큰은 엔티티에서 이미 지워졌으므로 지우기 전 값(암호문)을 실어 보낸다.
 */
data class UserWithdrawnEvent(
    val userId: Long,
    val appleRefreshToken: String?,
    val appleClientId: String?,
)
