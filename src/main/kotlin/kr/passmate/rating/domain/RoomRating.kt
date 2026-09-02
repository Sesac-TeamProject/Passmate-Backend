package kr.passmate.rating.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 세션이 끝난 뒤 참가자가 매기는 별점. 방·참가자당 한 번(uk_room_rating)이고 **수정할 수 없다**(FR-043).
 *
 * [hostUserId] 는 room 을 따라가면 알 수 있지만, 호스트 평균 별점을 뽑을 때마다
 * 방을 조인하지 않도록 비정규화해 둔다(ERD room_rating).
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

    /** 설명 명확·난이도 적당 같은 다중 선택 태그. enum 이름이 JSON 배열로 저장된다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags")
    val tags: List<RatingTag>? = null,

    /** 한 줄 후기. 호스트에게만 공개한다 */
    @Column(name = "comment", length = COMMENT_MAX)
    val comment: String? = null,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    init {
        // DTO 검증을 통과하지 않는 경로(배치·테스트)로도 들어오므로 엔티티에서 한 번 더 막는다
        if (stars !in STARS_MIN..STARS_MAX) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "별점은 $STARS_MIN~$STARS_MAX 사이여야 합니다.")
        }
        if ((comment?.length ?: 0) > COMMENT_MAX) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "한 줄 후기는 ${COMMENT_MAX}자를 넘을 수 없습니다.")
        }
    }

    companion object {
        const val COMMENT_MAX = 500
        const val STARS_MIN = 1
        const val STARS_MAX = 5
    }
}
