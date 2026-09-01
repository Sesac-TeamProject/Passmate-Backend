package kr.passmate.rating.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 세션이 끝난 뒤 참가자가 매기는 별점. 방·참가자당 한 번(uk_room_rating)이다.
 *
 * [hostUserId] 는 room 을 따라가면 알 수 있지만, 호스트 평균 별점을 뽑을 때마다
 * 방을 조인하지 않도록 비정규화해 둔다(ERD room_rating).
 * 제출 API 는 P3 라 지금은 "이미 평가했는지"만 읽는다.
 */
@Entity
@Table(name = "room_rating")
class RoomRating(
    @Column(name = "room_id", nullable = false, updatable = false)
    val roomId: Long,

    @Column(name = "participant_id", nullable = false, updatable = false)
    val participantId: Long,

    @Column(name = "host_user_id", nullable = false, updatable = false)
    val hostUserId: Long,

    /**
     * 1~5. DB 의 chk_room_rating_stars 와 이중으로 지킨다.
     * 컬럼이 TINYINT 라 JDBC 타입을 맞춰 준다 — 안 맞추면 ddl-auto: validate 가 기동을 막는다.
     */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "stars", nullable = false)
    val stars: Int,

    /** 설명 명확·난이도 적당 같은 다중 선택 태그 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags")
    val tags: List<String>? = null,

    /** 한 줄 후기. 호스트에게만 공개한다 */
    @Column(name = "comment", length = COMMENT_MAX)
    val comment: String? = null,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    companion object {
        const val COMMENT_MAX = 500
    }
}
