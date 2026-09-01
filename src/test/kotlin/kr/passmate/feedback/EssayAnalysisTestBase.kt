package kr.passmate.feedback

import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.ai.client.OpenAiClient
import kr.passmate.coin.repository.CoinTransactionRepository
import kr.passmate.coin.repository.CoinWalletRepository
import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.common.security.UserPrincipal
import kr.passmate.feedback.repository.AiFeedbackRepository
import kr.passmate.feedback.service.EssayAnalysisService
import kr.passmate.question.domain.QuestionType
import kr.passmate.question.dto.QuestionRequest
import kr.passmate.question.dto.QuestionSetCreateRequest
import kr.passmate.question.service.QuestionSetService
import kr.passmate.room.domain.RoomType
import kr.passmate.room.dto.JoinRoomRequest
import kr.passmate.room.dto.RoomCreateRequest
import kr.passmate.room.dto.RoomUpdateRequest
import kr.passmate.room.service.ParticipantService
import kr.passmate.room.service.RoomService
import kr.passmate.session.service.AnswerQueryService
import kr.passmate.support.FakeAiConfig
import kr.passmate.support.FakeOpenAiClient
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 서술형 AI 분석 테스트 공통 상차림 — 방·세트(서술형+객관식)·학생 답안까지 만들어 둔다.
 *
 * 무료 한도가 달라지면 확인할 것도 달라져서 테스트를 두 벌로 나눴다.
 * 한도를 남긴 쪽은 이 클래스를 그대로 쓰고, 소진된 쪽은 `essay-analysis-free-limit=0` 으로 덮는다.
 */
@AutoConfigureMockMvc
@Transactional
@Import(FakeAiConfig::class)
abstract class EssayAnalysisTestBase : IntegrationTestSupport() {

    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var objectMapper: ObjectMapper
    @Autowired protected lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired protected lateinit var openAiClient: OpenAiClient
    @Autowired protected lateinit var aiFeedbackRepository: AiFeedbackRepository
    @Autowired protected lateinit var coinWalletRepository: CoinWalletRepository
    @Autowired protected lateinit var coinTransactionRepository: CoinTransactionRepository
    @Autowired protected lateinit var essayAnalysisService: EssayAnalysisService
    @Autowired protected lateinit var answerQueryService: AnswerQueryService
    @Autowired protected lateinit var policy: PolicyProperties

    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roomService: RoomService
    @Autowired private lateinit var participantService: ParticipantService
    @Autowired private lateinit var questionSetService: QuestionSetService

    protected val fake: FakeOpenAiClient get() = openAiClient as FakeOpenAiClient

    protected var hostId: Long = 0
    protected var studentId: Long = 0
    protected var roomId: Long = 0
    protected var essayId: Long = 0
    protected var mcqId: Long = 0
    protected lateinit var hostToken: String
    protected lateinit var studentToken: String
    protected lateinit var guestToken: String

    @BeforeEach
    fun setUpSession() {
        fake.reset()
        hostId = member("essay-host")
        studentId = member("essay-student")
        hostToken = jwtTokenProvider.issue(hostId, false).accessToken
        studentToken = jwtTokenProvider.issue(studentId, false).accessToken

        val set = questionSetService.create(hostId, QuestionSetCreateRequest("네트워크"))
        essayId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.ESSAY, "TCP 를 설명하시오", answer = MODEL_ANSWER, timeLimitSec = 60, points = 200),
        ).id
        mcqId = questionSetService.addQuestion(
            set.id, hostId,
            QuestionRequest(QuestionType.MCQ, "404 는?", listOf("성공", "찾을 수 없음"), "찾을 수 없음", timeLimitSec = 30, points = 100),
        ).id
        questionSetService.confirm(set.id, hostId)

        val room = roomService.create(hostId, RoomCreateRequest(title = "네트워크 스터디", type = RoomType.FREE))
        roomService.update(room.id, hostId, RoomUpdateRequest(title = "네트워크 스터디", questionSetId = set.id))
        roomId = room.id

        participantService.join(roomId, studentId, JoinRoomRequest(nickname = "학생"))
        guestToken = participantService.join(roomId, null, JoinRoomRequest(nickname = "게스트")).accessToken!!

        startSessionAndSubmit()
    }

    /** 세션을 열고 학생이 서술형 답안을 낸다 — 분석은 답안이 있어야 시작된다. */
    private fun startSessionAndSubmit() {
        mockMvc.perform(post("/rooms/$roomId/session/start").header(AUTH, bearer(hostToken)))
            .andExpect(status().isNoContent)
        mockMvc.perform(
            post("/rooms/$roomId/session/questions/$essayId/answers")
                .header(AUTH, bearer(studentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("submitted" to SUBMITTED))),
        ).andExpect(status().isCreated)
    }

    protected fun requestAnalysis(token: String, questionId: Long): ResultActions =
        mockMvc.perform(
            post("/rooms/$roomId/session/questions/$questionId/answers/me/analysis").header(AUTH, bearer(token)),
        )

    protected fun myAnswer(token: String, questionId: Long): ResultActions =
        mockMvc.perform(
            get("/rooms/$roomId/session/questions/$questionId/answers/me").header(AUTH, bearer(token)),
        )

    /** 학생의 서술형 분석 행 id. 요청 뒤에만 있다. */
    protected fun feedbackId(): Long {
        val answerId = answerQueryService.getMyAnswer(roomId, essayId, UserPrincipal(studentId, false)).id
        return aiFeedbackRepository.findByAnswerId(answerId)!!.id
    }

    protected fun chargeWallet(amount: Int) {
        val wallet = coinWalletRepository.findByUserId(studentId)!!
        wallet.charge(amount)
        coinWalletRepository.saveAndFlush(wallet)
    }

    protected fun balance(): Int = coinWalletRepository.findByUserId(studentId)!!.balance

    protected fun ledger() = coinTransactionRepository.findAllByUserIdOrderByIdDesc(studentId)

    protected fun bearer(token: String) = "Bearer $token"

    private fun member(providerId: String): Long =
        userService.loginOrRegister(AuthProvider.GOOGLE, providerId, "$providerId@example.com", providerId, null).user.id

    protected companion object {
        const val AUTH = "Authorization"
        const val SUBMITTED = "연결을 맺고 데이터를 주고받는 프로토콜입니다."
        const val MODEL_ANSWER = "연결지향 프로토콜"
    }
}
