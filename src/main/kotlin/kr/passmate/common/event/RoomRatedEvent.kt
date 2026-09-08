package kr.passmate.common.event

/**
 * 참가자가 방을 평가했다.
 *
 * 호스트 평판(host_profile 의 평균 별점·평가 수)은 hostlevel 기능이 집계하는데, rating 이 hostlevel 을
 * 직접 부르면 hostlevel → room → rating 쪽 의존과 얽힌다. 세션 종료와 같은 방식으로 이벤트로 끊는다.
 * 별점은 세션이 끝난 뒤 24시간 안에 들어오므로, 종료 시점 집계만으로는 명성 화면이 다음 세션이나
 * 월 배치까지 옛 값을 보였다(2026-09-09 시나리오 테스트 S-06).
 */
data class RoomRatedEvent(val roomId: Long, val hostUserId: Long)
