package kr.passmate.coin.repository

import kr.passmate.coin.domain.CoinRefType
import kr.passmate.coin.domain.CoinTransaction
import kr.passmate.coin.domain.CoinTransactionType
import org.springframework.data.jpa.repository.JpaRepository

interface CoinTransactionRepository : JpaRepository<CoinTransaction, Long> {

    fun findAllByUserIdOrderByIdDesc(userId: Long): List<CoinTransaction>

    /**
     * 같은 대상에 같은 종류의 줄이 이미 있는지. 환급을 두 번 넣지 않기 위한 멱등 검사다.
     * (ref_type, ref_id) 에 인덱스가 있어 조회가 싸다.
     */
    fun existsByRefTypeAndRefIdAndType(refType: CoinRefType, refId: Long, type: CoinTransactionType): Boolean
}
