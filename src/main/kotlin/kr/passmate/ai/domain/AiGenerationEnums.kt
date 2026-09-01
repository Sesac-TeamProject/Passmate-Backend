package kr.passmate.ai.domain

/** 무엇을 생성했는지. 무료 한도는 SET 만 센다(REGENERATE·FILE 은 세지 않는다). */
enum class AiGenerationKind {
    /** 조건을 받아 문항 여러 개를 만들어 세트에 추가 */
    SET,

    /** 기존 문항 하나를 같은 조건으로 다시 생성 */
    REGENERATE,

    /** 강의자료 파일 기반 생성 (P2, 아직 미구현) */
    FILE,
}

enum class AiGenerationStatus {
    SUCCESS,
    FAILED,
}
