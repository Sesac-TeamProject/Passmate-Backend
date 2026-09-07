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

    /**
     * 이 방에서만 쓰는 문항별 제한시간(questionId → 초). 확정 세트는 불변이라 세트 대신 방이 덮어쓴다
     * (W-02b 문항별 시간 설정, 웹 버그 리포트 B-18). 세션 시작 시 session_question.time_limit_sec 로 복사된다.
     * NULL = 전부 세트 기본값.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_time_overrides")
    var questionTimeOverrides: Map<Long, Int>? = null
        protected set

    /**
     * 자동 넘김을 켠 문항 id 목록(W-02b 토글, 2026-09-07). NULL = 전부 꺼짐.
     * 시간 만료로 마감된 문항이 여기 있으면 서버가 잠시 뒤 다음 문항을 자동 개시한다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_auto_advance")
    var questionAutoAdvance: List<Long>? = null
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
        // 덮어쓴 시간·자동 넘김은 예전 세트의 문항 id 를 가리킨다 — 세트가 바뀌면 의미가 없으니 비운다
        if (questionSetId != this.questionSetId) {
            questionTimeOverrides = null
            questionAutoAdvance = null
        }
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

    /**
     * 문항별 시간·자동 넘김을 통째로 갈아끼운다(전체 교체 — 빈 값이면 전부 기본으로 돌아간다). 대기 중일 때만.
     * 문항이 세트에 있는지·시간 범위는 Service 가 세트를 읽어 검사한다 — 방은 세트 내용을 모른다.
     */
    fun overrideQuestionTimes(times: Map<Long, Int>, autoAdvance: Collection<Long> = emptyList()) {
        verifyWaiting("문항별 시간은 대기 중일 때만 바꿀 수 있습니다.")
        questionTimeOverrides = times.takeIf { it.isNotEmpty() }
        questionAutoAdvance = autoAdvance.distinct().sorted().takeIf { it.isNotEmpty() }
    }

    /** 이 문항이 시간 만료로 마감되면 다음 문항을 자동으로 열지. */
    fun isAutoAdvance(questionId: Long): Boolean =
        questionAutoAdvance?.contains(questionId) == true

    /** 이 방에서 쓸 제한시간. 덮어쓴 값이 없으면 세트에 적힌 기본값이다. */
    fun timeLimitSecOf(questionId: Long, default: Int): Int =
        questionTimeOverrides?.get(questionId) ?: default

    fun hasTimeOverride(questionId: Long): Boolean =
        questionTimeOverrides?.containsKey(questionId) == true

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
