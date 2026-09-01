package kr.passmate.report.repository

import kr.passmate.report.domain.ParticipantReport
import org.springframework.data.jpa.repository.JpaRepository

interface ParticipantReportRepository : JpaRepository<ParticipantReport, Long> {

    fun findByParticipantId(participantId: Long): ParticipantReport?

    /** 세션 종료 때 참가자 전원의 리포트를 한 번에 찍는다 — 건건이 조회하면 N+1 이다. */
    fun findAllByParticipantIdIn(participantIds: Collection<Long>): List<ParticipantReport>
}
