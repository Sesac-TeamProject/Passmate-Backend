package kr.passmate.report.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.feedback.dto.AnalysisStatus
import kr.passmate.feedback.dto.EssayAnalysisView
import kr.passmate.feedback.dto.TeacherReviewView
import kr.passmate.question.domain.QuestionType
import kr.passmate.rating.dto.RatingAvailability
import kr.passmate.room.domain.RoomStatus
import java.time.LocalDateTime

/** 방 리포트 요약 (W-07 개요 탭). */
@Schema(description = "세션 요약 지표")
data class ResultSummary(
    val participantCount: Int,
    val questionCount: Int,
    @field:Schema(description = "채점된 답안 중 정답 비율(%). 서술형은 채점 전이라 빠진다")
    val avgCorrectRate: Double,
    @field:Schema(description = "참가자 1인당 평균 점수. 한 문제도 안 푼 사람도 분모에 든다")
    val avgScore: Double,
    @field:Schema(description = "분석이 끝난 건수. 진행 중·실패는 세지 않는다")
    val aiAnalysisCount: Int,
)

/** 문항별 정답률 (W-07 문항별 탭). */
@Schema(description = "문항 한 줄")
data class QuestionResultRow(
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    val type: QuestionType,
    val content: String,
    val points: Int,
    val submitCount: Int,
    val correctCount: Int,
    val correctRate: Double,
    val aiAnalysisCount: Int,
    @field:Schema(description = "문항 단위 선생님 코멘트(학생 전체 대상, W-07). 답안별 첨삭과 별개. 없으면 null")
    val teacherComment: String?,
)

/** 학생별 점수·순위 (W-07 학생별 탭). */
@Schema(description = "참가자 한 줄")
data class ParticipantResultRow(
    val rank: Int,
    val participantId: Long,
    val nickname: String,
    val avatarId: String,
    val totalScore: Long,
    val correctCount: Int,
    val submitCount: Int,
)

@Schema(description = "세션 전체 통계 — 호스트 전용")
data class SessionResultsResponse(
    val roomId: Long,
    val title: String,
    val status: RoomStatus,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val summary: ResultSummary,
    val questions: List<QuestionResultRow>,
    val participants: List<ParticipantResultRow>,
)

/**
 * 문항 하나에 대한 내 결과. 미제출 문항도 줄이 나온다 —
 * 빠뜨린 문제를 화면에서 지우면 학생은 뭘 놓쳤는지 알 수 없다.
 */
@Schema(description = "문항별 결과")
data class AnswerResultView(
    val sessionQuestionId: Long,
    val questionId: Long,
    val orderNo: Int,
    val type: QuestionType,
    val content: String,
    val points: Int,
    @field:Schema(description = "문항 주제. 세트에 주제를 적지 않았으면 null")
    val topic: String?,
    @field:Schema(description = "마감된 문항에만 실린다 — 진행 중에 내보내면 정답이 샌다")
    val answer: String?,
    val explanation: String?,
    @field:Schema(description = "제출하지 않았으면 null")
    val submitted: String?,
    val isCorrect: Boolean?,
    val score: Int,
    @field:Schema(description = "첨삭 보정이 반영된 최종 점수")
    val finalScore: Int,
    @field:Schema(description = "이 문항의 반 정답률(%). 채점된 답안이 분모라 서술형은 0. 마감 전에는 null — 진행 중 분포는 호스트만 본다")
    val correctRate: Double?,
    @field:Schema(description = "문항이 열린 뒤 제출까지 걸린 시간(ms). 미제출이면 null")
    val elapsedMs: Long?,
    val analysisStatus: AnalysisStatus,
    val analysis: EssayAnalysisView?,
    val teacherReview: TeacherReviewView?,
    @field:Schema(description = "문항 단위 선생님 코멘트 — 학생 전체에게 남긴 첨삭(M-06). 내 답안별 첨삭(teacherReview)과 별개")
    val teacherComment: String?,
)

@Schema(description = "내 세션 결과")
data class MySessionResultResponse(
    val roomId: Long,
    val roomTitle: String,
    val status: RoomStatus,
    val endedAt: LocalDateTime?,
    val participantId: Long,
    val nickname: String,
    val avatarId: String,
    @field:Schema(description = "게스트면 true — 종료 화면에서 가입을 유도한다")
    val guest: Boolean,
    val rank: Int,
    val totalScore: Long,
    val correctCount: Int,
    val submitCount: Int,
    val questionCount: Int,
    @field:Schema(description = "순위의 분모. 중도 이탈자도 포함 — 결과 집계와 같은 인원")
    val participantCount: Int,
    @field:Schema(description = "제출한 문항의 소요 시간 합(ms). 아무것도 안 냈으면 null")
    val elapsedMs: Long?,
    val questions: List<AnswerResultView>,
    val rating: RatingAvailability,
)

@Schema(description = "학생별 결과 상세 — 호스트 전용")
data class ParticipantResultResponse(
    val roomId: Long,
    val participantId: Long,
    val nickname: String,
    val avatarId: String,
    val rank: Int,
    val totalScore: Long,
    val correctCount: Int,
    val submitCount: Int,
    val questionCount: Int,
    @field:Schema(description = "순위의 분모. 중도 이탈자도 포함 — 결과 집계와 같은 인원")
    val participantCount: Int,
    @field:Schema(description = "제출한 문항의 소요 시간 합(ms). 아무것도 안 냈으면 null")
    val elapsedMs: Long?,
    val questions: List<AnswerResultView>,
)
