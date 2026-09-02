package kr.passmate.common.event

/**
 * 첨삭으로 답안 점수가 바뀌었다.
 *
 * 점수 하나가 바뀌면 그 방의 **등수 전체**가 흔들린다 — 바뀐 사람 한 명이 아니라
 * 방 단위로 다시 계산해야 한다. 그래서 participantId 가 아니라 roomId 를 싣는다.
 *
 * feedback 이 report·session 을 직접 부르면 순환 참조라 이 이벤트로 끊는다
 * (§아키텍처 "순환 참조가 생기면 common/event 로"). 리스너는 **같은 트랜잭션에서 동기로** 돈다 —
 * 첨삭 응답을 받은 화면이 곧바로 갱신된 등수를 다시 읽는다.
 */
data class AnswerScoreAdjustedEvent(val roomId: Long)
