package kr.passmate.user.domain

enum class UserStatus {
    /** 정상 */
    ACTIVE,

    /** 제재로 정지 — 로그인·입장이 차단된다(FR-063) */
    SUSPENDED,

    /** 탈퇴 */
    DELETED,
}
