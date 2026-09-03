package kr.passmate.moderation.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.common.security.GuestPrincipal
import kr.passmate.common.security.UserPrincipal
import kr.passmate.moderation.domain.Report
import kr.passmate.moderation.domain.ReportTargetType
import kr.passmate.moderation.dto.ReportRequest
import kr.passmate.moderation.repository.ReportRepository
import kr.passmate.question.service.QuestionSetQueryService
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신고 접수 (FR-067). 게스트도 낼 수 있어 [AuthPrincipal] 을 그대로 받는다.
 *
 * 접수만 한다 — 검토·처리·제재 연계는 관리자 콘솔 몫이다.
 */
@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val userService: UserService,
    private val roomQueryService: RoomQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val questionSetQueryService: QuestionSetQueryService,
) {

    @Transactional
    fun report(principal: AuthPrincipal, request: ReportRequest): Report {
        verifyTargetExists(request)
        verifyNotSelf(principal, request)
        verifyNotDuplicated(principal, request)

        return reportRepository.save(
            Report(
                reporterUserId = (principal as? UserPrincipal)?.userId,
                reporterParticipantId = (principal as? GuestPrincipal)?.participantId,
                targetType = request.targetType,
                targetId = request.targetId,
                type = request.type,
                reason = request.reason.trim(),
            ),
        )
    }

    /**
     * 없는 대상에 대한 신고는 받지 않는다 — 관리자 목록에 열어 볼 수 없는 줄이 쌓이고,
     * id 를 바꿔 가며 무엇이 존재하는지 떠보는 통로가 된다.
     */
    private fun verifyTargetExists(request: ReportRequest) {
        when (request.targetType) {
            ReportTargetType.USER -> userService.getActiveUser(request.targetId)
            ReportTargetType.ROOM -> roomQueryService.getRoom(request.targetId)
            ReportTargetType.PARTICIPANT -> participantQueryService.get(request.targetId)
            ReportTargetType.QUESTION -> questionSetQueryService.verifyQuestionExists(request.targetId)
        }
    }

    private fun verifyNotSelf(principal: AuthPrincipal, request: ReportRequest) {
        val userId = (principal as? UserPrincipal)?.userId ?: return
        val self = request.targetType == ReportTargetType.USER && request.targetId == userId
        if (self) throw BusinessException(ErrorCode.INVALID_INPUT, "자기 자신은 신고할 수 없습니다.")
    }

    /**
     * 같은 사람이 같은 대상을 반복 접수하면 미처리 목록이 한 사건으로 도배된다.
     * 처리 상태와 무관하게 한 번만 받는다.
     */
    private fun verifyNotDuplicated(principal: AuthPrincipal, request: ReportRequest) {
        val already = when (principal) {
            is UserPrincipal -> reportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                principal.userId, request.targetType, request.targetId,
            )

            is GuestPrincipal -> reportRepository.existsByReporterParticipantIdAndTargetTypeAndTargetId(
                principal.participantId, request.targetType, request.targetId,
            )
        }
        if (already) throw BusinessException(ErrorCode.CONFLICT, "이미 신고한 대상입니다.")
    }
}
