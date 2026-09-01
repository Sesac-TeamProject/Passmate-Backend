package kr.passmate.question.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.question.domain.Question
import kr.passmate.question.domain.QuestionSet
import kr.passmate.question.domain.QuestionSetStatus
import kr.passmate.question.repository.QuestionRepository
import kr.passmate.question.repository.QuestionSetRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class QuestionSetQueryService(
    private val questionSetRepository: QuestionSetRepository,
    private val questionRepository: QuestionRepository,
) {

    /** 내 세트 목록. 방 만들기의 세트 선택에서는 status=CONFIRMED 로 걸러 쓴다. */
    fun list(ownerUserId: Long, status: QuestionSetStatus?, pageable: Pageable): Page<QuestionSet> =
        status
            ?.let { questionSetRepository.findAllByOwnerUserIdAndStatusAndDeletedAtIsNull(ownerUserId, it, pageable) }
            ?: questionSetRepository.findAllByOwnerUserIdAndDeletedAtIsNull(ownerUserId, pageable)

    /** 세트 + 문항 목록. 정답·해설이 함께 나가므로 소유자만 볼 수 있다. */
    fun getDetail(setId: Long, ownerUserId: Long): Pair<QuestionSet, List<Question>> {
        val set = getOwnedSet(setId, ownerUserId)
        return set to questionRepository.findAllBySetIdOrderByOrderNoAsc(setId)
    }

    fun getOwnedSet(setId: Long, ownerUserId: Long): QuestionSet {
        val set = questionSetRepository.findByIdAndDeletedAtIsNull(setId)
            ?: throw BusinessException(ErrorCode.QUESTION_SET_NOT_FOUND)
        set.verifyOwner(ownerUserId)
        return set
    }
}
