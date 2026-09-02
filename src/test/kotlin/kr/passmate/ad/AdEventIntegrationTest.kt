package kr.passmate.ad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.passmate.ad.domain.AdCampaign
import kr.passmate.ad.domain.AdPlacement
import kr.passmate.ad.repository.AdCampaignRepository
import kr.passmate.ad.repository.AdEventRepository
import kr.passmate.common.security.JwtTokenProvider
import kr.passmate.support.IntegrationTestSupport
import kr.passmate.user.domain.AuthProvider
import kr.passmate.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 집행 중 판정과 노출·클릭 집계 (FR-072 · FR-073).
 *
 * 캠페인 등록은 관리자 콘솔 몫이라 여기서는 리포지터리로 직접 깔아 둔다.
 */
@AutoConfigureMockMvc
@Transactional
class AdEventIntegrationTest : IntegrationTestSupport() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adCampaignRepository: AdCampaignRepository
    @Autowired private lateinit var adEventRepository: AdEventRepository

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        token = jwtTokenProvider.issue(
            userService.loginOrRegister(AuthProvider.GOOGLE, "ad-user", null, "ad-user", null).user.id,
            false,
        ).accessToken
    }

    @Test
    fun `집행 중인 광고만 그 자리에 걸린다`() {
        val active = campaign("집행 중", AdPlacement.RESULT_BOTTOM, activate = true)
        campaign("검수 대기", AdPlacement.RESULT_BOTTOM, activate = false)

        val ads = ads(AdPlacement.RESULT_BOTTOM)

        assertThat(ads.map { it.get("adId").asLong() }).containsExactly(active.id)
        assertThat(ads[0].get("advertiser").asText()).isEqualTo("집행 중")
        assertThat(ads[0].get("creativeUrl").asText()).isNotEmpty()
        assertThat(ads[0].get("linkUrl").asText()).isNotEmpty()
    }

    @Test
    fun `계약 금액은 응답에 담기지 않는다`() {
        campaign("집행 중", AdPlacement.HOME_CARD, activate = true)

        assertThat(ads(AdPlacement.HOME_CARD)[0].has("contractAmount")).isFalse()
    }

    @Test
    fun `다른 자리의 광고는 걸리지 않는다`() {
        campaign("홈용", AdPlacement.HOME_CARD, activate = true)

        assertThat(ads(AdPlacement.RESULT_BOTTOM)).isEmpty()
    }

    @Test
    fun `기간이 지난 광고는 걸리지 않는다`() {
        campaign(
            "지난 광고", AdPlacement.REPORT_BOTTOM, activate = true,
            startsAt = LocalDate.now().minusDays(10), endsAt = LocalDate.now().minusDays(1),
        )

        assertThat(ads(AdPlacement.REPORT_BOTTOM)).isEmpty()
    }

    @Test
    fun `아직 시작하지 않은 광고는 걸리지 않는다`() {
        campaign(
            "예정 광고", AdPlacement.REPORT_BOTTOM, activate = true,
            startsAt = LocalDate.now().plusDays(1), endsAt = LocalDate.now().plusDays(10),
        )

        assertThat(ads(AdPlacement.REPORT_BOTTOM)).isEmpty()
    }

    @Test
    fun `노출을 기록하면 캠페인 집계가 올라간다`() {
        val ad = campaign("집계", AdPlacement.HOME_CARD, activate = true)

        event(ad.id, "IMPRESSION").andExpect(status().isNoContent)
        event(ad.id, "IMPRESSION").andExpect(status().isNoContent)
        event(ad.id, "CLICK").andExpect(status().isNoContent)

        val reloaded = adCampaignRepository.findById(ad.id).get()
        assertThat(reloaded.impressions).isEqualTo(2)
        assertThat(reloaded.clicks).isEqualTo(1)
    }

    @Test
    fun `이벤트는 행으로도 남는다 — 누적 숫자로는 언제 몇 번을 못 답한다`() {
        val ad = campaign("원장", AdPlacement.HOME_CARD, activate = true)

        event(ad.id, "IMPRESSION").andExpect(status().isNoContent)

        val events = adEventRepository.findAll().filter { it.campaignId == ad.id }
        assertThat(events).hasSize(1)
        assertThat(events[0].occurredAt).isNotNull()
    }

    @Test
    fun `없는 광고에는 집계할 수 없다`() {
        event(999_999, "IMPRESSION")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `모르는 이벤트 종류는 거절한다`() {
        val ad = campaign("집계", AdPlacement.HOME_CARD, activate = true)

        event(ad.id, "SCROLL").andExpect(status().isBadRequest)
    }

    @Test
    fun `로그인하지 않아도 집계할 수 있다`() {
        val ad = campaign("게스트 집계", AdPlacement.WAITING_ROOM_BANNER, activate = true)

        // 대기실 배너는 게스트도 본다. 그 노출이 집계에서 빠지면 광고주 수치가 실제보다 낮게 잡힌다
        mockMvc.perform(
            post("/ads/{id}/events", ad.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"IMPRESSION"}"""),
        ).andExpect(status().isNoContent)

        assertThat(adCampaignRepository.findById(ad.id).get().impressions).isEqualTo(1)
    }

    // ---------- helpers ----------

    private fun campaign(
        advertiser: String,
        placement: AdPlacement,
        activate: Boolean,
        startsAt: LocalDate = LocalDate.now().minusDays(1),
        endsAt: LocalDate = LocalDate.now().plusDays(7),
    ): AdCampaign {
        val campaign = AdCampaign(
            name = "$advertiser 캠페인",
            advertiser = advertiser,
            placement = placement,
            creativeUrl = "https://cdn.example.com/$advertiser.png",
            linkUrl = "https://example.com/$advertiser",
            startsAt = startsAt,
            endsAt = endsAt,
            contractAmount = 1_000_000,
        )
        if (activate) campaign.activate()
        return adCampaignRepository.saveAndFlush(campaign)
    }

    private fun ads(placement: AdPlacement): List<JsonNode> =
        mockMvc.perform(get("/ads").param("placement", placement.name))
            .andExpect(status().isOk).andReturn().json().get("ads").toList()

    private fun event(adId: Long, type: String): ResultActions = mockMvc.perform(
        post("/ads/{id}/events", adId)
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"type":"$type"}"""),
    )

    private fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)
}
