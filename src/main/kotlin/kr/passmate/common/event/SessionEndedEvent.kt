package kr.passmate.common.event

/**
 * 세션이 끝났다.
 *
 * session 과 report 는 서로를 필요로 한다 — 리포트는 답안·문항을 읽어야 하고,
 * 세션 종료는 리포트를 만들어야 한다. 생성자 주입으로 이으면 순환 참조로 기동이 막히므로
 * 이 이벤트로 끊는다(§아키텍처 "순환 참조가 생기면 common/event 로").
 *
 * 리스너는 **같은 트랜잭션에서 동기로** 돈다 — 종료 직후 결과 화면이 곧바로 리포트를 읽는다.
 */
data class SessionEndedEvent(val roomId: Long)
