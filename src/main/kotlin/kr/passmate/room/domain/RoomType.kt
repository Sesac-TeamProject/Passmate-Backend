package kr.passmate.room.domain

enum class RoomType {
    /** 무료 — 게스트도 입장 가능 */
    FREE,

    /** 유료 — 회원 전용, 참가비를 코인에서 차감 (호스트 Lv.3 이상) */
    PAID,

    /** 브랜디드 퀴즈 — 기업 위탁 */
    BRANDED,
}
