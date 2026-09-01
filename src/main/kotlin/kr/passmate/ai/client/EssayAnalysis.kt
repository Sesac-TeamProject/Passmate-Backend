package kr.passmate.ai.client

/**
 * 서술형 답안 분석 요청.
 *
 * [submitted] 는 **학생이 쓴 글**이다 — 지시문과 섞지 않고 별도 컨텍스트 블록으로 넣는다
 * (프롬프트 인젝션 완화). [modelAnswer] 는 출제자가 적어 둔 채점 기준이다.
 */
data class EssayAnalysisRequest(
    val questionContent: String,
    val modelAnswer: String,
    val submitted: String,
)

/**
 * 분석 결과. 화면(M-06)의 "핵심 포함 / 부족한 부분 / 개선 방향" 세 칸에 그대로 들어간다.
 * ai_feedback 의 key_points · missing_points · suggestions · summary 와 1:1 이다.
 */
data class EssayAnalysisResult(
    /** 답안이 짚은 핵심 */
    val keyPoints: List<String>,
    /** 모범답안에 있는데 답안에서 빠진 부분 */
    val missingPoints: List<String>,
    /** 다음에 어떻게 쓰면 좋은지 */
    val suggestions: List<String>,
    /** 두세 문장 총평 */
    val summary: String,
    val model: String,
    val durationMs: Int,
)
