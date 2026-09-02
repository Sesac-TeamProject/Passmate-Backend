package kr.passmate.question.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.question.domain.ContentSource
import kr.passmate.question.domain.Difficulty
import kr.passmate.question.domain.Question
import kr.passmate.question.domain.QuestionSet
import kr.passmate.question.domain.QuestionSetStatus
import kr.passmate.question.domain.QuestionSource
import kr.passmate.question.domain.QuestionType
import java.time.LocalDateTime

@Schema(description = "문제 세트 요약 — 목록·방 만들기의 세트 선택에서 쓴다")
data class QuestionSetSummaryResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val status: QuestionSetStatus,
    val source: ContentSource?,
    val questionCount: Int,
    val totalPoints: Int,
    val estimatedSeconds: Int?,
    val usageCount: Int,
    val lastUsedAt: LocalDateTime?,
    val confirmedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
) {
    companion object {
        fun from(set: QuestionSet) = QuestionSetSummaryResponse(
            id = set.id,
            title = set.title,
            description = set.description,
            status = set.status,
            source = set.source,
            questionCount = set.questionCount,
            totalPoints = set.totalPoints,
            estimatedSeconds = set.estimatedSeconds,
            usageCount = set.usageCount,
            lastUsedAt = set.lastUsedAt,
            confirmedAt = set.confirmedAt,
            createdAt = set.createdAt,
        )
    }
}

@Schema(description = "문항")
data class QuestionResponse(
    val id: Long,
    val orderNo: Int,
    val type: QuestionType,
    val content: String,
    val choices: List<String>?,
    @field:Schema(description = "정답. 세트 편집 화면에서만 내려간다 — 세션 진행 중에는 절대 포함하지 않는다")
    val answer: String?,
    val explanation: String?,
    val topic: String?,
    val difficulty: Difficulty?,
    val timeLimitSec: Int,
    val points: Int,
    val source: QuestionSource,
) {
    companion object {
        fun from(question: Question) = QuestionResponse(
            id = question.id,
            orderNo = question.orderNo,
            type = question.type,
            content = question.content,
            choices = question.choices,
            answer = question.answer,
            explanation = question.explanation,
            topic = question.topic,
            difficulty = question.difficulty,
            timeLimitSec = question.timeLimitSec,
            points = question.points,
            source = question.source,
        )
    }
}

@Schema(description = "문제 세트 상세 — 문항 목록 포함")
data class QuestionSetDetailResponse(
    val set: QuestionSetSummaryResponse,
    val questions: List<QuestionResponse>,
) {
    companion object {
        fun of(set: QuestionSet, questions: List<Question>) = QuestionSetDetailResponse(
            set = QuestionSetSummaryResponse.from(set),
            questions = questions.map(QuestionResponse::from),
        )
    }
}
