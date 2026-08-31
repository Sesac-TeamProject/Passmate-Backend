package kr.passmate.common.exception

import org.springframework.http.HttpStatus

/**
 * 응답 오류 코드. code 는 enum 이름 그대로 나가고, 클라이언트는 이 값으로 분기한다.
 * 기능을 구현하면서 해당 기능의 코드를 이 enum 에 추가한다.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    // 401 — 토큰 만료는 반드시 401. 403 으로 응답하면 클라이언트 refresh 가 발화하지 않는다
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // 402
    INSUFFICIENT_COINS(HttpStatus.PAYMENT_REQUIRED, "코인이 부족합니다."),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_ROOM_HOST(HttpStatus.FORBIDDEN, "방의 호스트만 할 수 있습니다."),
    HOST_LEVEL_REQUIRED(HttpStatus.FORBIDDEN, "유료 방은 Lv.3 이상부터 개설할 수 있습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "제재 중인 계정입니다."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),

    // 409
    CONFLICT(HttpStatus.CONFLICT, "이미 처리되었거나 충돌하는 요청입니다."),

    // 500 / 502
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 문제 생성에 실패했습니다. 다시 시도해 주세요."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 호출에 실패했습니다."),
}
