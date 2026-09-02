package kr.passmate.report.service

import kr.passmate.common.event.AnswerScoreAdjustedEvent
import kr.passmate.common.event.SessionEndedEvent
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.common.security.AuthPrincipal
import kr.passmate.report.domain.ParticipantReport
import kr.passmate.report.dto.LearningReportResponse
import kr.passmate.report.repository.ParticipantReportRepository
import kr.passmate.room.domain.RoomStatus
import kr.passmate.room.service.ParticipantQueryService
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.service.AnswerQueryService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 개인 학습 리포트 (FR-030).
 *
 * 세션이 끝나면 참가자마다 한 장씩 찍는다. 매번 계산해도 나오는 값이지만 남겨 두는 이유는,
 * 나중에 문항 자체가 수정돼도 "그때 그 세션의 결과"는 그대로여야 하기 때문이다.
 *
 * 다만 **첨삭으로 점수가 바뀌면 다시 찍는다** — 그건 선생님이 결과를 고치려고 한 일이라
 * 스냅샷을 지키면 리포트의 총점과 결과 화면의 총점이 어긋난 채로 남는다.
 */
@Service
class ParticipantReportService(
    private val roomQueryService: RoomQueryService,
    private val participantQueryService: ParticipantQueryService,
    private val answerQueryService: AnswerQueryService,
    private val materialsLoader: SessionMaterialsLoader,
    private val reportRepository: ParticipantReportRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 세션 종료 신호를 받아 리포트를 찍는다.
     *
     * **같은 트랜잭션에서 동기로** 돈다(AFTER_COMMIT 이 아니다) — 종료 직후 결과 화면이
     * 곧바로 리포트를 읽는데, 커밋 뒤로 미루면 그 사이 조회가 빈손으로 돌아온다.
     */
    @EventListener
    @Transactional
    fun onSessionEnded(event: SessionEndedEvent) {
        val count = generate(event.roomId).size
        log.info("학습 리포트를 만들었다 roomId={} count={}", event.roomId, count)
    }

    /**
     * 첨삭으로 점수가 바뀌면 그 방 리포트를 다시 찍는다.
     *
     * 한 명의 점수가 바뀌면 **등수는 전원이 흔들린다.** 바뀐 답안의 주인만 다시 찍으면
     * 나머지 사람 리포트에 옛 등수가 남는다.
     *
     * 아직 안 끝난 세션은 건너뛴다 — 리포트는 종료 시점에 처음 생긴다.
     */
    @EventListener
    @Transactional
    fun onAnswerScoreAdjusted(event: AnswerScoreAdjustedEvent) {
        if (roomQueryService.getRoom(event.roomId).status != RoomStatus.ENDED) return
        val count = generate(event.roomId).size
        log.info("첨삭 반영으로 학습 리포트를 다시 찍었다 roomId={} count={}", event.roomId, count)
    }

    /** 방 전원의 리포트를 찍는다. 이미 있으면 덮어쓴다 — 다시 불러도 행이 늘지 않는다. */
    @Transactional
    fun generate(roomId: Long): List<ParticipantReport> {
        val room = roomQueryService.getRoom(roomId)
        val materials = materialsLoader.load(room)
        val existing = reportRepository
            .findAllByParticipantIdIn(materials.participants.map { it.id })
            .associateBy { it.participantId }

        return materials.participants.map { participant ->
            val id = participant.id
            val weakTopics = materials.weakTopicsOf(id).takeIf { it.isNotEmpty() }
            existing[id]?.also {
                it.refresh(
                    totalQuestions = materials.sessionQuestions.size,
                    correctCount = materials.correctCountOf(id),
                    totalScore = materials.scoreOf(id).toInt(),
                    finalRank = materials.rankOf(id),
                    weakTopics = weakTopics,
                )
            } ?: reportRepository.save(
                ParticipantReport(
                    participantId = id,
                    totalQuestions = materials.sessionQuestions.size,
                    correctCount = materials.correctCountOf(id),
                    totalScore = materials.scoreOf(id).toInt(),
                    finalRank = materials.rankOf(id),
                    weakTopics = weakTopics,
                ),
            )
        }
    }

    /**
     * 내 리포트. 없으면 **그 자리에서 찍어 준다.**
     *
     * 조회가 쓰기를 하는 건 이례적이지만, 세션이 끝난 방인데 리포트가 없다는 건
     * 종료 이벤트가 돌기 전에 만들어진 데이터라는 뜻이다. 학생에게 404 를 주는 것보다
     * 한 번 만들어 주는 편이 낫고, 참가자당 한 행이라 여러 번 불려도 안전하다.
     */
    @Transactional
    fun myReport(roomId: Long, principal: AuthPrincipal): LearningReportResponse {
        val room = roomQueryService.getRoom(roomId)
        if (room.status != RoomStatus.ENDED) {
            throw BusinessException(ErrorCode.SESSION_NOT_RUNNING, "세션이 끝나야 학습 리포트가 만들어집니다.")
        }

        val participantId = answerQueryService.resolveParticipantId(roomId, principal)
        val participant = participantQueryService.getOfRoom(roomId, participantId)
        val report = reportRepository.findByParticipantId(participantId)
            ?: generate(roomId).firstOrNull { it.participantId == participantId }
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "학습 리포트를 만들 수 없습니다.")

        return LearningReportResponse.of(roomId, room.title, participant.nickname, report)
    }
}
