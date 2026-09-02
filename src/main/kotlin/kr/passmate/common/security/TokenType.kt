package kr.passmate.common.security

/** 토큰 용도. 다른 용도의 토큰으로 API 를 부르지 못하게 막는다. */
enum class TokenType {
    ACCESS,
    REFRESH,

    /** 게스트 참가자용 — 입장한 방 하나에만 유효하다 */
    GUEST,
}
