package kr.passmate.feedback.service

/**
 * "분석을 요청받았다" — 커밋된 뒤에 실제 호출을 시작하기 위한 신호.
 *
 * 요청 트랜잭션 안에서 바로 OpenAI 를 부르면 수십 초 동안 커넥션을 쥐고 있게 되고,
 * 커밋 전이라 PENDING 행이 아직 보이지도 않는다. 그래서 AFTER_COMMIT 으로 미룬다.
 *
 * 호출에 필요한 글을 전부 실어 보낸다 — 비동기 스레드가 DB 를 다시 읽지 않아도 되게.
 */
data class EssayAnalysisRequestedEvent(
    val feedbackId: Long,
    val questionContent: String,
    val modelAnswer: String,
    val submitted: String,
)
