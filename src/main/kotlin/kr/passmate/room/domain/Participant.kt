package kr.passmate.room.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseTimeEntity
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import java.time.LocalDateTime

/**
 * 방 참가자. `user_id` 가 NULL 이면 게스트다 — 게스트는 guest_token 으로 자기 자신을 증명한다.
 * 닉네임은 DB 유니크(room_id, nickname)로 방 안에서만 유일하다.
 */
@Entity
@Table(name = "participant")
class Participant(
    @Column(name = "room_id", nullable = false, updatable = false)
    val roomId: Long,

    @Column(name = "nickname", nullable = false, length = 30)
    val nickname: String,

    @Column(name = "avatar_id", nullable = false, length = 30)
    val avatarId: String,

    /** NULL = 게스트 */
    @Column(name = "user_id", updatable = false)
    val userId: Long? = null,

    /** 게스트 식별자. 회원이면 NULL */
    @Column(name = "guest_token", length = 64, updatable = false)
    val guestToken: String? = null,

    /** 게스트 제재를 기기 기준으로 걸기 위한 값(FR-063) */
    @Column(name = "device_key", length = 64)
    val deviceKey: String? = null,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now(),
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ParticipantStatus = ParticipantStatus.JOINED
        protected set

    @Column(name = "left_at")
    var leftAt: LocalDateTime? = null
        protected set

    @Column(name = "total_score", nullable = false)
    var totalScore: Int = 0
        protected set

    @Column(name = "final_rank")
    var finalRank: Int? = null
        protected set

    /** 게스트 기록을 계정에 연동한 시각(FR-030) */
    @Column(name = "claimed_at")
    var claimedAt: LocalDateTime? = null
        protected set

    val isGuest: Boolean get() = userId == null

    /** 본인이 나감. */
    fun leave(at: LocalDateTime = LocalDateTime.now()) {
        verifyJoined()
        status = ParticipantStatus.LEFT
        leftAt = at
    }

    /** 호스트가 내보냄. */
    fun kick(at: LocalDateTime = LocalDateTime.now()) {
        verifyJoined()
        status = ParticipantStatus.KICKED
        leftAt = at
    }

    private fun verifyJoined() {
        if (status != ParticipantStatus.JOINED) {
            throw BusinessException(ErrorCode.CONFLICT, "이미 방을 나간 참가자입니다.")
        }
    }
}
