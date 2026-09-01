package kr.passmate.rating.repository

import kr.passmate.rating.domain.RoomRating
import org.springframework.data.jpa.repository.JpaRepository

interface RoomRatingRepository : JpaRepository<RoomRating, Long> {

    fun existsByRoomIdAndParticipantId(roomId: Long, participantId: Long): Boolean

    fun findAllByRoomId(roomId: Long): List<RoomRating>
}
