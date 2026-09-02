package kr.passmate.moderation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.passmate.moderation.domain.Report
import kr.passmate.moderation.domain.ReportStatus
import kr.passmate.moderation.domain.ReportTargetType
import kr.passmate.moderation.domain.ReportType
import java.time.LocalDateTime

@Schema(description = "신고 접수")
data class ReportRequest(
    @field:Schema(description = "USER · PARTICIPANT · QUESTION · ROOM")
    @field:NotNull
    val targetType: ReportTargetType,

    @field:NotNull
    val targetId: Long,

    @field:Schema(description = "NICKNAME · QUESTION_ERROR · PAID_ROOM · OPERATION · SPAM · DIFFICULTY")
    @field:NotNull
    val type: ReportType,

    @field:NotBlank(message = "신고 사유는 필수입니다.")
    @field:Size(max = Report.REASON_MAX)
    val reason: String,
)

/**
 * 접수 결과. **누가 냈는지는 돌려주지 않는다** — 신고자가 자기 신고를 확인하는 화면이라
 * 이미 아는 값이고, 남에게 보일 일이 있으면 안 되는 값이다.
 */
@Schema(description = "접수된 신고")
data class ReportResponse(
    val id: Long,
    val targetType: ReportTargetType,
    val targetId: Long,
    val type: ReportType,
    val reason: String,
    val status: ReportStatus,
    val createdAt: LocalDateTime?,
) {
    companion object {
        fun from(report: Report) = ReportResponse(
            id = report.id,
            targetType = report.targetType,
            targetId = report.targetId,
            type = report.type,
            reason = report.reason,
            status = report.status,
            createdAt = report.createdAt,
        )
    }
}
