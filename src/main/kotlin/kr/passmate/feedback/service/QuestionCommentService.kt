package kr.passmate.feedback.service

import kr.passmate.feedback.domain.SessionQuestionComment
import kr.passmate.feedback.dto.QuestionCommentRequest
import kr.passmate.feedback.dto.QuestionCommentResponse
import kr.passmate.feedback.repository.SessionQuestionCommentRepository
import kr.passmate.room.service.RoomQueryService
import kr.passmate.session.service.SessionQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 문항 단위 선생님 코멘트 (W-07 우측 패널, 2026-09-07 B-14).
 *
 * 답안별 첨삭(TeacherReview)과 달리 **학생 전체에게** 남기는 글이라 점수 보정이 없다 —
 * 저장만 하고, 리포트 조회가 문항 줄에 실어 내려보낸다.
 */
@Service
class QuestionCommentService(
    private val roomQueryService: RoomQueryService,
    private val sessionQueryService: SessionQueryService,
    private val commentRepository: SessionQuestionCommentRepository,
) {

    /** 코멘트를 저장한다(upsert). 문항당 한 장이라 다시 저장하면 덮어쓴다. 호스트만. */
    @Transactional
    fun upsert(
        roomId: Long,
        questionId: Long,
        hostUserId: Long,
        request: QuestionCommentRequest,
    ): QuestionCommentResponse {
        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        // 이 방에서 출제된 문항이어야 한다 — 남의 방 문항 id 를 끼워 넣어도 통과하지 않게
        val sq = sessionQueryService.findSessionQuestion(roomId, questionId)

        val comment = commentRepository.findBySessionQuestionId(sq.id)
            ?.apply { update(request.comment) }
            ?: commentRepository.save(
                SessionQuestionComment(
                    sessionQuestionId = sq.id,
                    reviewerUserId = hostUserId,
                    comment = request.comment,
                ),
            )
        return QuestionCommentResponse.from(roomId, questionId, comment)
    }
}

/** 리포트 조회가 문항 줄에 코멘트를 붙일 때 쓰는 읽기 창구. */
@Service
@Transactional(readOnly = true)
class QuestionCommentQueryService(
    private val commentRepository: SessionQuestionCommentRepository,
) {

    /** session_question id → 코멘트 본문. 문항마다 따로 조회하면 N+1 이라 한 번에 받는다. */
    fun commentsOf(sessionQuestionIds: Collection<Long>): Map<Long, String> {
        if (sessionQuestionIds.isEmpty()) return emptyMap()
        return commentRepository.findAllBySessionQuestionIdIn(sessionQuestionIds)
            .associate { it.sessionQuestionId to it.comment }
    }
}
