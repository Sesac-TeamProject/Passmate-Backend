package kr.passmate.feedback.domain

/** 서술형 분석 진행 상태. `ai_feedback.status` 와 값이 같다. */
enum class AiFeedbackStatus {
    /** 요청을 받아 큐에 넣었다. 응답은 이 상태로 즉시 돌아간다 */
    PENDING,

    DONE,

    /** 실패. 차감분은 환급되고, 같은 답안으로 다시 요청할 수 있다 */
    FAILED,
}
