package kr.passmate.moderation.domain

/** 신고 대상 (FR-067). `report.target_type` 문자열과 1:1 이다. */
enum class ReportTargetType {
    /** 회원 계정 — 주로 호스트 */
    USER,

    /** 세션 안의 참가자 한 명 — 닉네임 신고가 여기로 온다 */
    PARTICIPANT,

    /** 문항 — 정답 오류·난이도 */
    QUESTION,

    /** 방·세션 운영 */
    ROOM,
}

/** 신고 유형. 화면의 라디오 항목과 같다(닉네임·문제 오류·유료 방·운영·도배·난이도). */
enum class ReportType {
    NICKNAME,
    QUESTION_ERROR,
    PAID_ROOM,
    OPERATION,
    SPAM,
    DIFFICULTY,
}

/** 처리 상태. 접수는 항상 OPEN 으로 시작한다. */
enum class ReportStatus {
    OPEN,
    REVIEWING,
    RESOLVED,
}
