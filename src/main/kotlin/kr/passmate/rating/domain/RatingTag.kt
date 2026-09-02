package kr.passmate.rating.domain

/**
 * 별점과 함께 고르는 태그(M-06 v2 · FR-042). 다중 선택이고 `room_rating.tags` 에 JSON 배열로 들어간다.
 *
 * DB 에는 **enum 이름**이 저장된다 — 화면 문구가 바뀌어도 이미 쌓인 평가가 깨지지 않는다.
 * [label] 은 호스트 집계 화면이 그대로 쓰도록 응답에 함께 실어 보낸다.
 */
enum class RatingTag(val label: String) {
    CLEAR_EXPLANATION("설명이 명확해요"),
    FAIR_DIFFICULTY("난이도가 적당해요"),
    GOOD_PACING("시간 배분이 좋아요"),
    HELPFUL_HINT("힌트가 도움됐어요"),
    GOOD_QUESTIONS("문제 품질이 좋아요"),
}
