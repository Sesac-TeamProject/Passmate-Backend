package kr.passmate.ad.domain

/** 광고 노출 위치 (FR-073). `ad_campaign.placement` 문자열과 1:1 이다. */
enum class AdPlacement {
    /** 최종 결과 화면 하단 */
    RESULT_BOTTOM,

    /** 대기실 배너 */
    WAITING_ROOM_BANNER,

    /** 리포트 하단 */
    REPORT_BOTTOM,

    /** 홈 카드 */
    HOME_CARD,
}

/** 캠페인 상태. 등록은 검수 대기로 시작하고 관리자가 승인해야 집행된다. */
enum class AdCampaignStatus {
    PENDING_REVIEW,
    ACTIVE,
    ENDED,
}

/** 집계 이벤트 (FR-073). */
enum class AdEventType { IMPRESSION, CLICK }
