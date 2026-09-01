package kr.passmate.rating.repository

import kr.passmate.rating.domain.RoomRating
import org.springframework.data.jpa.repository.JpaRepository

interface RoomRatingRepository : JpaRepository<RoomRating, Long> {

    fun existsByRoomIdAndParticipantId(roomId: Long, participantId: Long): Boolean

    fun findAllByRoomId(roomId: Long): List<RoomRating>

    /** 이 호스트가 받은 평가 전부. 명성 요약(평균 별점)이 쓴다 — idx_room_rating_host 가 받쳐 준다. */
    fun findAllByHostUserId(hostUserId: Long): List<RoomRating>
}
