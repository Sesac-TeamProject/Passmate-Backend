package kr.passmate.room.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/** 공개 방 목록 정렬(FR-054). */
enum class PublicRoomSort {
    /** 인기 — 운영 중 우선, 그다음 참여 인원 많은 순 */
    POPULAR,

    /** 예정 — 시작 시각 빠른 순, 시각 없는 방은 뒤로 */
    UPCOMING,
}

/** 방 유형 필터. 전체를 보려면 파라미터를 비운다. */
enum class PublicRoomTypeFilter {
    FREE,
    PAID,
}

/** 진행 상태 필터. 종료된 방은 어느 쪽으로도 조회되지 않는다. */
enum class PublicRoomStatusFilter {
    /** 예정 — 아직 시작하지 않은 방 */
    WAITING,

    /** 운영 중 */
    RUNNING,
}

@Schema(description = "공개 방 목록 조회 조건 — 전부 선택")
data class PublicRoomSearchRequest(
    @field:Schema(description = "검색어. 방 이름·주제 태그·선생님 닉네임에서 찾는다")
    val q: String? = null,

    @field:Schema(description = "무료/유료. 비우면 전체")
    val type: PublicRoomTypeFilter? = null,

    @field:Schema(description = "오늘 진행 예정인 방만")
    val today: Boolean = false,

    @field:Schema(description = "예정/운영 중. 비우면 둘 다")
    val status: PublicRoomStatusFilter? = null,

    val sort: PublicRoomSort = PublicRoomSort.POPULAR,

    @field:Schema(description = "0부터")
    @field:Min(0)
    val page: Int = 0,

    @field:Schema(description = "한 페이지 개수. 1~50")
    @field:Min(1)
    @field:Max(MAX_PAGE_SIZE)
    val size: Int = DEFAULT_PAGE_SIZE,
) {
    /**
     * **Spring 의 `Pageable` 을 컨트롤러에서 받지 않는다.**
     *
     * 두 가지 이유다. 첫째, 그 바인딩은 `sort` 라는 이름의 쿼리 파라미터를 정렬 필드로 읽어
     * 우리 `sort=POPULAR` 와 충돌한다(`order by … r.POPULAR asc` 가 붙어 500 이 났다).
     * 둘째, 정렬은 JPQL 에 고정해 두는 게 맞다 — 클라이언트가 아무 필드로나
     * 정렬하게 두면 인덱스 없는 컬럼으로 전체 정렬이 걸린다.
     */
    fun toPageable(): Pageable = PageRequest.of(page, size)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50L
    }
}
