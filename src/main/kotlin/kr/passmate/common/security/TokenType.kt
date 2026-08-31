package kr.passmate.common.security

/** 토큰 용도. refresh 토큰으로 API 를 호출하거나 그 반대를 하지 못하게 막는다. */
enum class TokenType {
    ACCESS,
    REFRESH,
}
