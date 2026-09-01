package kr.passmate.support

import kr.passmate.ai.client.AiCallException
import kr.passmate.ai.client.AiGenerationRequest
import kr.passmate.ai.client.AiGenerationResult
import kr.passmate.ai.client.EssayAnalysisRequest
import kr.passmate.ai.client.EssayAnalysisResult
import kr.passmate.ai.client.GeneratedQuestion
import kr.passmate.ai.client.OpenAiClient
import kr.passmate.question.domain.QuestionType

/**
 * 테스트용 OpenAI 클라이언트. **네트워크를 타지 않으므로 요금이 발생하지 않는다.**
 *
 * 기본은 요청한 유형·개수 그대로 그럴듯한 문항을 만들어 준다.
 * `failTimes` 로 앞선 n 번을 실패시켜 재시도 동작을 확인한다.
 */
class FakeOpenAiClient : OpenAiClient {

    /** 지금까지 호출된 횟수. 재시도가 실제로 한 번만 도는지 세는 데 쓴다 */
    var callCount: Int = 0
        private set

    var lastRequest: AiGenerationRequest? = null
        private set

    /** 서술형 분석 호출 횟수. 자동 실행이 아니라 요청할 때만 도는지 세는 데 쓴다 */
    var analysisCallCount: Int = 0
        private set

    var lastAnalysisRequest: EssayAnalysisRequest? = null
        private set

    private var failuresLeft: Int = 0
    private var failureRetryable: Boolean = true
    private var scripted: List<GeneratedQuestion>? = null
    private var analysisFailuresLeft: Int = 0
    private var analysisFailureRetryable: Boolean = true

    /** 앞선 [times] 번 호출을 실패시킨다. [retryable] = false 면 재시도 대상이 아니다. */
    fun failTimes(times: Int, retryable: Boolean = true) {
        failuresLeft = times
        failureRetryable = retryable
    }

    /** 이 결과를 그대로 돌려준다. 유형·개수 검증까지 흉내내지 않는다. */
    fun respondWith(questions: List<GeneratedQuestion>) {
        scripted = questions
    }

    /** 앞선 [times] 번 분석 호출을 실패시킨다. 환급 경로를 확인할 때 쓴다. */
    fun failAnalysisTimes(times: Int, retryable: Boolean = true) {
        analysisFailuresLeft = times
        analysisFailureRetryable = retryable
    }

    fun reset() {
        callCount = 0
        lastRequest = null
        failuresLeft = 0
        failureRetryable = true
        scripted = null
        analysisCallCount = 0
        lastAnalysisRequest = null
        analysisFailuresLeft = 0
        analysisFailureRetryable = true
    }

    override fun analyzeEssay(request: EssayAnalysisRequest): EssayAnalysisResult {
        analysisCallCount++
        lastAnalysisRequest = request

        if (analysisFailuresLeft > 0) {
            analysisFailuresLeft--
            throw AiCallException("테스트용 분석 실패", retryable = analysisFailureRetryable)
        }

        return EssayAnalysisResult(
            keyPoints = listOf("핵심을 짚었습니다"),
            missingPoints = listOf("근거가 부족합니다"),
            suggestions = listOf("예시를 하나 덧붙여 보세요"),
            summary = "전반적으로 방향은 맞습니다.",
            model = FAKE_MODEL,
            durationMs = 1,
        )
    }

    override fun generateQuestions(request: AiGenerationRequest): AiGenerationResult {
        callCount++
        lastRequest = request

        if (failuresLeft > 0) {
            failuresLeft--
            throw AiCallException("테스트용 실패", retryable = failureRetryable)
        }

        return AiGenerationResult(
            questions = scripted ?: defaultQuestions(request),
            model = FAKE_MODEL,
            durationMs = 1,
        )
    }

    private fun defaultQuestions(request: AiGenerationRequest): List<GeneratedQuestion> =
        request.counts.entries
            .filter { it.value > 0 }
            .flatMap { (type, count) -> (1..count).map { index -> question(type, request, index) } }

    private fun question(type: QuestionType, request: AiGenerationRequest, index: Int) = when (type) {
        QuestionType.MCQ -> GeneratedQuestion(
            type = QuestionType.MCQ,
            content = "${request.topic} 객관식 $index",
            choices = listOf("보기1", "보기2", "보기3", "보기4"),
            answer = "보기1",
            explanation = "해설 $index",
            difficulty = request.difficulty,
        )

        QuestionType.OX -> GeneratedQuestion(
            type = QuestionType.OX,
            content = "${request.topic} OX $index",
            choices = null,
            answer = "O",
            explanation = "해설 $index",
            difficulty = request.difficulty,
        )

        QuestionType.ESSAY -> GeneratedQuestion(
            type = QuestionType.ESSAY,
            content = "${request.topic} 서술형 $index",
            choices = null,
            answer = "모범답안 $index",
            explanation = null,
            difficulty = request.difficulty,
        )
    }

    companion object {
        const val FAKE_MODEL = "fake-model"
    }
}
