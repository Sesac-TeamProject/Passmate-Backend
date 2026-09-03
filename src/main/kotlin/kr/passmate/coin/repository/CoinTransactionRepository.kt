package kr.passmate.coin.repository

import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransaction
import kr.passmate.coin.domain.CoinTransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoinTransactionRepository : JpaRepository<CoinTransaction, Long> {

    fun findAllByUserIdOrderByIdDesc(userId: Long): List<CoinTransaction>

    /**
     * 내역 화면용 페이징. **id 내림차순**으로 정렬한다 —
     * created_at 은 같은 트랜잭션에서 남긴 줄끼리 값이 같아 순서가 흔들린다.
     */
    @Query("select t from CoinTransaction t where t.userId = :userId order by t.id desc")
    fun findAllByUserId(@Param("userId") userId: Long, pageable: Pageable): Page<CoinTransaction>

    /** 마이페이지 코인 카드에 붙는 가장 최근 한 건. */
    fun findFirstByUserIdOrderByIdDesc(userId: Long): CoinTransaction?

    /**
     * 같은 대상에 같은 종류의 줄이 이미 있는지. 환급을 두 번 넣지 않기 위한 멱등 검사다.
     * (ref_type, ref_id) 에 인덱스가 있어 조회가 싸다.
     */
    fun existsByRefTypeAndRefIdAndType(refType: CoinRefType, refId: Long, type: CoinTransactionType): Boolean
}
