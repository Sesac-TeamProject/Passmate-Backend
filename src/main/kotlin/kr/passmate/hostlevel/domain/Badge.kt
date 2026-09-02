package kr.passmate.hostlevel.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.passmate.common.domain.BaseCreatedEntity

/**
 * 뱃지 정의 8종 (FR-048). 코드가 아니라 표로 두는 이유는
 * user_badge 가 FK 로 이걸 가리키기 때문이다 — 목록은 V3 마이그레이션이 넣는다.
 */
@Entity
@Table(name = "badge")
class Badge(
    @Column(name = "code", nullable = false, length = 50, updatable = false)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "description", length = 300)
    var description: String? = null,

    @Column(name = "condition_type", length = 30)
    var conditionType: String? = null,

    /** 조건 문턱. 별점은 10배로 담긴다(45 = 4.5) — [BadgeConditionType.target] 이 되돌린다 */
    @Column(name = "condition_value")
    var conditionValue: Int? = null,

    @Column(name = "icon_url", length = 500)
    var iconUrl: String? = null,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0
        protected set

    val condition: BadgeConditionType? get() = BadgeConditionType.of(conditionType)

    /** 목표치. 조건이 없으면 null — 수동 부여 뱃지를 나중에 넣어도 화면이 깨지지 않게. */
    val target: Double? get() = condition?.target(conditionValue ?: 0)
}
