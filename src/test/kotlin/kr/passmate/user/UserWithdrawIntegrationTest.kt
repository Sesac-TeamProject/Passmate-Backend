package kr.passmate.user

import kr.passmate.coin.repository.CoinTransactionRepository
import kr.passmate.coin.repository.CoinWalletRepository
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.service.RoomService
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.domain.User
import kr.passmate.user.domain.UserStatus
import kr.passmate.user.repository.UserRepository
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 탈퇴. 기록은 남기되 누구였는지를 지우고, 남은 토큰이 더는 통하지 않는지 본다.
 */
@AutoConfigureMockMvc
@Transactional
class UserWithdrawIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var coinWalletRepository: CoinWalletRepository
    @Autowired private lateinit var coinTransactionRepository: CoinTransactionRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private var userId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val outcome = userService.loginOrRegister(
            AuthProvider.GOOGLE, "bye-user", "bye@example.com", "혜림", "https://img.example/a.png",
        )
        userId = outcome.user.id
        token = jwtTokenProvider.issue(userId, outcome.user.isAdmin).accessToken
    }

    @Test
    fun `탈퇴하면 계정이 내려가고 개인정보가 지워진다`() {
        withdraw().andExpect(status().isNoContent)

        val user = userRepository.findById(userId).orElseThrow()
        assertThat(user.status).isEqualTo(UserStatus.DELETED)
        assertThat(user.deletedAt).isNotNull()
        // 참여 기록이 FK 로 가리키므로 행은 남기고 누구였는지만 지운다
        assertThat(user.email).isNull()
        assertThat(user.profileImageUrl).isNull()
        assertThat(user.nickname).isEqualTo(User.WITHDRAWN_NICKNAME)
    }

    @Test
    fun `탈퇴 뒤에는 남은 토큰이 통하지 않는다`() {
        mockMvc.perform(get("/users/me").header(AUTH, "Bearer $token")).andExpect(status().isOk)

        withdraw().andExpect(status().isNoContent)

        // stateless JWT 라 토큰 자체는 아직 안 만료됐지만 계정이 없으므로 401 이어야 한다
        mockMvc.perform(get("/users/me").header(AUTH, "Bearer $token"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
    }

    @Test
    fun `보유 코인은 소멸하고 원장에 그 기록이 남는다`() {
        val wallet = coinWalletRepository.findByUserId(userId)!!
        wallet.charge(500)
        coinWalletRepository.saveAndFlush(wallet)

        withdraw().andExpect(status().isNoContent)

        assertThat(coinWalletRepository.findByUserId(userId)!!.balance).isZero()
        val entry = coinTransactionRepository.findAllByUserIdOrderByIdDesc(userId).single()
        assertThat(entry.amount).isEqualTo(-500)
        assertThat(entry.balanceAfter).isZero()
        assertThat(entry.memo).isEqualTo("회원 탈퇴로 코인 소멸")
    }

    @Test
    fun `코인이 없으면 원장에 아무것도 남기지 않는다`() {
        withdraw().andExpect(status().isNoContent)

        assertThat(coinTransactionRepository.findAllByUserIdOrderByIdDesc(userId)).isEmpty()
    }

    @Test
    fun `아직 안 끝난 방이 있으면 탈퇴할 수 없다`() {
        roomService.create(userId, RoomCreateRequest(title = "진행 예정", type = RoomType.FREE))

        withdraw()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))

        assertThat(userRepository.findById(userId).orElseThrow().status).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    fun `두 번 탈퇴할 수 없다`() {
        withdraw().andExpect(status().isNoContent)

        withdraw().andExpect(status().isUnauthorized)
    }

    private fun withdraw(): ResultActions =
        mockMvc.perform(delete("/users/me").header(AUTH, "Bearer $token"))

    private companion object {
        const val AUTH = "Authorization"
    }
}
