package kr.passmate.moderation.repository

import kr.passmate.moderation.domain.Report
import kr.passmate.moderation.domain.ReportTargetType
import org.springframework.data.jpa.repository.JpaRepository

interface ReportRepository : JpaRepository<Report, Long> {

    /** 회원이 같은 대상을 이미 신고했는지. 같은 사람이 같은 대상을 반복 접수하는 것을 막는다. */
    fun existsByReporterUserIdAndTargetTypeAndTargetId(
        reporterUserId: Long,
        targetType: ReportTargetType,
        targetId: Long,
    ): Boolean

    /** 게스트는 계정이 없어 참가자 id 로 본다. */
    fun existsByReporterParticipantIdAndTargetTypeAndTargetId(
        reporterParticipantId: Long,
        targetType: ReportTargetType,
        targetId: Long,
    ): Boolean
}
