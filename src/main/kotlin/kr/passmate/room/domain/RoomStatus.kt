package kr.passmate.room.domain

enum class RoomStatus {
    /** 대기실 — 참가자 입장 가능, 호스트가 수정·취소 가능 */
    WAITING,

    /** 세션 진행 중 */
    RUNNING,

    /** 정상 종료 */
    ENDED,

    /** 시작 전 취소 */
    CANCELED,
    ;

    /** PIN 은 활성 방 사이에서만 유일하다. 종료된 방의 PIN 은 재사용된다. */
    val isActive: Boolean get() = this == WAITING || this == RUNNING
}
