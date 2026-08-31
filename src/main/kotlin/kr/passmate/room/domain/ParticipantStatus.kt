package kr.passmate.room.domain

enum class ParticipantStatus {
    /** 입장 중 */
    JOINED,

    /** 본인이 나감 */
    LEFT,

    /** 호스트가 내보냄 */
    KICKED,
}
