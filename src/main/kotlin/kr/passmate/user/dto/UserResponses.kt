package kr.passmate.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.passmate.room.service.UserRoomStats
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.domain.User
import java.time.LocalDateTime

@Schema(description = "마이페이지 요약 지표 (C-02 v3 · M-12)")
data class MyStatsResponse(
    @field:Schema(description = "참여한 방 수")
    val joinedRoomCount: Long,
    @field:Schema(description = "내가 만든 방 수")
    val hostedRoomCount: Long,
    @field:Schema(description = "진행한 세션 수 — 시작해서 종료까지 간 방만 센다")
    val hostedSessionCount: Long,
    @field:Schema(description = "누적 학생 수 — 종료된 내 방들의 참가자 합")
    val totalStudentCount: Long,
) {
    companion object {
        fun from(stats: UserRoomStats) = MyStatsResponse(
            joinedRoomCount = stats.joinedRoomCount,
            hostedRoomCount = stats.hostedRoomCount,
            hostedSessionCount = stats.hostedSessionCount,
            totalStudentCount = stats.totalStudentCount,
        )
    }
}

/**
 * 내 정보. 회원 유형은 단일이고 참여·개설은 행위에 따른 역할이라
 * 학생 지표와 호스트 지표가 한 응답에 함께 나간다.
 *
 * 등급·뱃지·평균 별점·평가 수는 **아직 없다** — hostlevel·rating 기능을 붙일 때
 * 이 응답에 필드를 더한다. 값을 0·null 로 미리 내보내면 "등급 없음"으로 읽혀 오해를 만든다.
 */
@Schema(description = "내 정보 조회 응답")
data class MyProfileResponse(
    val id: Long,
    val nickname: String,
    val email: String?,
    @field:Schema(description = "로그인 방식")
    val provider: AuthProvider,
    val profileImageUrl: String?,
    @field:Schema(description = "기본 캐릭터. 방 입장 시 참가자 아바타의 기본값이 된다")
    val defaultAvatarId: String?,
    val isAdmin: Boolean,
    @field:Schema(description = "가입일")
    val joinedAt: LocalDateTime,
    val lastLoginAt: LocalDateTime?,
    val stats: MyStatsResponse,
    @field:Schema(description = "보유 코인 (1 C = 1원)")
    val coinBalance: Int,
) {
    companion object {
        fun of(user: User, stats: UserRoomStats, coinBalance: Int) = MyProfileResponse(
            id = user.id,
            nickname = user.nickname,
            email = user.email,
            provider = user.provider,
            profileImageUrl = user.profileImageUrl,
            defaultAvatarId = user.defaultAvatarId,
            isAdmin = user.isAdmin,
            joinedAt = user.createdAt,
            lastLoginAt = user.lastLoginAt,
            stats = MyStatsResponse.from(stats),
            coinBalance = coinBalance,
        )
    }
}
