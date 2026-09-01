package kr.passmate.common.exception

import org.springframework.http.HttpStatus

/**
 * 응답 오류 코드. code 는 enum 이름 그대로 나가고, 클라이언트는 이 값으로 분기한다.
 * 기능을 구현하면서 해당 기능의 코드를 이 enum 에 추가한다.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),
    UNSUPPORTED_ROOM_TYPE(HttpStatus.BAD_REQUEST, "아직 지원하지 않는 방 유형입니다."),
    INVALID_QUESTION(HttpStatus.BAD_REQUEST, "문항 내용이 올바르지 않습니다."),

    // 401 — 토큰 만료는 반드시 401. 403 으로 응답하면 클라이언트 refresh 가 발화하지 않는다
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "소셜 로그인 인증에 실패했습니다. 다시 시도해 주세요."),

    // 402
    INSUFFICIENT_COINS(HttpStatus.PAYMENT_REQUIRED, "코인이 부족합니다."),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_ROOM_HOST(HttpStatus.FORBIDDEN, "방의 호스트만 할 수 있습니다."),
    NOT_QUESTION_SET_OWNER(HttpStatus.FORBIDDEN, "본인이 만든 문제 세트만 다룰 수 있습니다."),
    HOST_LEVEL_REQUIRED(HttpStatus.FORBIDDEN, "유료 방은 Lv.3 이상부터 개설할 수 있습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "제재 중인 계정입니다."),
    GUEST_NOT_ALLOWED(HttpStatus.FORBIDDEN, "회원만 이용할 수 있습니다. 로그인해 주세요."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    QUESTION_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 세트를 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "참가자를 찾을 수 없습니다."),

    // 409
    CONFLICT(HttpStatus.CONFLICT, "이미 처리되었거나 충돌하는 요청입니다."),
    ROOM_NOT_JOINABLE(HttpStatus.CONFLICT, "지금은 입장할 수 없는 방입니다."),
    ROOM_FULL(HttpStatus.CONFLICT, "정원이 가득 찼습니다."),
    SESSION_NOT_RUNNING(HttpStatus.CONFLICT, "진행 중인 세션이 아닙니다."),
    QUESTION_NOT_RUNNING(HttpStatus.CONFLICT, "지금 풀 수 있는 문항이 아닙니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출한 문항입니다."),
    SESSION_ALREADY_FINISHED(HttpStatus.CONFLICT, "모든 문항이 끝났습니다."),
    QUESTION_SET_REQUIRED(HttpStatus.CONFLICT, "확정된 문제 세트를 먼저 연결해 주세요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 입장한 방입니다."),
    QUESTION_SET_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 문제 세트입니다."),
    QUESTION_SET_EMPTY(HttpStatus.CONFLICT, "문항이 하나도 없는 세트는 확정할 수 없습니다."),
    // 429 — 무료 한도 소진. 생성용 코인 정책이 정해지면 402 로 바뀔 자리다
    AI_FREE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI 문항 생성 무료 횟수를 모두 사용했습니다."),

    PIN_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "방 PIN 발급에 실패했습니다. 다시 시도해 주세요."),

    // 500 / 502
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 문제 생성에 실패했습니다. 다시 시도해 주세요."),
    AI_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "AI 답변 분석에 실패했습니다. 다시 시도해 주세요."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 호출에 실패했습니다."),
}
