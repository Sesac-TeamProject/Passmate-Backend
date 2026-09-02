package kr.passmate.common.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/** 목록 응답 공통 형식. Spring 의 Page 를 그대로 내보내지 않고 필요한 필드만 고정한다. */
@Schema(description = "페이지 목록")
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun <E, T> from(page: Page<E>, mapper: (E) -> T) = PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
        )
    }
}
