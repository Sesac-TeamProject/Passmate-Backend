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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 방 = 세션 1회. 호스트가 직접 종료한다(ERD room).
 * 상태 전이(start·close·cancel)와 그 검증은 전부 이 클래스 안에서 한다.
 *
 * 다른 기능의 엔티티(User·QuestionSet)는 참조하지 않고 식별자만 들고 있는다.
 */
@Entity
@Table(name = "room")
class Room(
    @Column(name = "host_user_id", nullable = false, updatable = false)
    val hostUserId: Long,

    // DDL 이 CHAR(6) 이라 JDBC 타입을 맞춰준다. VARCHAR 로 두면 ddl-auto validate 가 막는다
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pin", nullable = false, length = 6)
    val pin: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: RoomType,

    @Column(name = "title", nullable = false, length = 100)
    var title: String,

    @Column(name = "description", length = 500)
    var description: String? = null,

    @Column(name = "topic", length = 50)
    var topic: String? = null,

    @Column(name = "question_set_id")
    var questionSetId: Long? = null,

    @Column(name = "fee")
    val fee: Int? = null,

    @Column(name = "max_participants")
    var maxParticipants: Int? = null,

    @Column(name = "is_public", nullable = false)
    var isPublic: Boolean = false,

    @Column(name = "scheduled_at")
    var scheduledAt: LocalDateTime? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RoomStatus = RoomStatus.WAITING
        protected set

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null
        protected set

    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null
        protected set

    @Column(name = "current_question_no", nullable = false)
    var currentQuestionNo: Int = 0
        protected set

    @Column(name = "screen_locked", nullable = false)
    var screenLocked: Boolean = false
        protected set

    @Column(name = "participant_count", nullable = false)
    var participantCount: Int = 0
        protected set

    @Column(name = "avg_score", precision = 7, scale = 2)
    var avgScore: java.math.BigDecimal? = null
        protected set

    @Column(name = "correct_rate", precision = 5, scale = 2)
    var correctRate: java.math.BigDecimal? = null
        protected set

    /** 호스트인지 확인하고, 아니면 403 으로 막는다. */
    fun verifyHost(userId: Long) {
        if (userId != hostUserId) throw BusinessException(ErrorCode.NOT_ROOM_HOST)
    }

    /** 대기실에서만 고칠 수 있다(API 명세: 대기 상태에서만). */
    fun update(
        title: String,
        description: String?,
        topic: String?,
        questionSetId: Long?,
        maxParticipants: Int?,
        isPublic: Boolean,
        scheduledAt: LocalDateTime?,
    ) {
        verifyWaiting("방 정보는 대기 중일 때만 수정할 수 있습니다.")
        this.title = title
        this.description = description
        this.topic = topic
        this.questionSetId = questionSetId
        this.maxParticipants = maxParticipants
        this.isPublic = isPublic
        this.scheduledAt = scheduledAt
    }

    /**
     * 방을 닫는다. 시작 전이면 취소(CANCELED), 진행 중이었으면 종료(ENDED).
     * 어느 쪽이든 PIN 은 이 시점부터 다른 방이 다시 쓸 수 있다.
     */
    fun close(at: LocalDateTime = LocalDateTime.now()): RoomStatus {
        status = when (status) {
            RoomStatus.WAITING -> RoomStatus.CANCELED
            RoomStatus.RUNNING -> RoomStatus.ENDED
            else -> throw BusinessException(ErrorCode.CONFLICT, "이미 종료된 방입니다.")
        }
        endedAt = at
        return status
    }

    /** 세션을 시작한다. 대기 중일 때만 가능하고, 이후 참가자 입장은 막힌다. */
    fun start(at: LocalDateTime = LocalDateTime.now()) {
        if (status != RoomStatus.WAITING) {
            throw BusinessException(ErrorCode.CONFLICT, "대기 중인 방만 시작할 수 있습니다.")
        }
        status = RoomStatus.RUNNING
        startedAt = at
    }

    /** 다음 문항으로 넘어간다. 진행 중일 때만 가능하다. */
    fun advanceQuestion(orderNo: Int) {
        if (status != RoomStatus.RUNNING) {
            throw BusinessException(ErrorCode.CONFLICT, "진행 중인 방이 아닙니다.")
        }
        currentQuestionNo = orderNo
    }

    fun verifyRunning() {
        if (status != RoomStatus.RUNNING) {
            throw BusinessException(ErrorCode.SESSION_NOT_RUNNING)
        }
    }

    /**
     * 세션이 끝났을 때 결과 요약을 박아 둔다(ERD room.avg_score · correct_rate).
     * 목록 화면이 방마다 답안을 다시 세지 않게 하려는 값이다.
     */
    fun recordResult(avgScore: java.math.BigDecimal, correctRate: java.math.BigDecimal) {
        this.avgScore = avgScore
        this.correctRate = correctRate
    }

    fun lockScreen(locked: Boolean) {
        screenLocked = locked
    }

    fun increaseParticipantCount() {
        participantCount += 1
    }

    fun decreaseParticipantCount() {
        if (participantCount > 0) participantCount -= 1
    }

    /** 정원이 찼는지. maxParticipants 가 null 이면 제한 없음. */
    fun isFull(): Boolean = maxParticipants?.let { participantCount >= it } ?: false

    /** 지금 입장할 수 있는 상태인지 확인한다. */
    fun verifyJoinable() {
        if (status != RoomStatus.WAITING) {
            throw BusinessException(ErrorCode.ROOM_NOT_JOINABLE)
        }
        if (isFull()) {
            throw BusinessException(ErrorCode.ROOM_FULL)
        }
    }

    private fun verifyWaiting(message: String) {
        if (status != RoomStatus.WAITING) throw BusinessException(ErrorCode.CONFLICT, message)
    }
}
