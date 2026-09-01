package kr.passmate.session.domain

/**
 * WebSocket 으로 내보내는 이벤트 종류.
 *
 * ⚠️ **QUESTION_STARTED 에는 정답을 절대 넣지 않는다.** WS 페이로드는 브라우저 개발자도구에서
 * 그대로 보인다. 정답과 응답 분포는 QUESTION_ENDED 에만 실린다.
 */
enum class SessionEventType {
    SESSION_STARTED,

    /** 문항 시작 — 지문·보기·endsAt 만. 정답 없음 */
    QUESTION_STARTED,

    /** 문항 마감 — 여기서 처음 정답·응답 분포·정답률이 나간다 */
    QUESTION_ENDED,

    /** 랭킹 갱신 */
    RANKING_UPDATED,

    /** 제출 현황(호스트 전용 토픽) */
    SUBMISSION_UPDATED,

    SESSION_ENDED,

    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
}
