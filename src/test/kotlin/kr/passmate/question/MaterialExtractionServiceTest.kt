package kr.passmate.question

import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.question.dto.AiGenerateRequest
import kr.passmate.question.service.MaterialExtractionService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

/**
 * 강의자료 본문 추출. 파일은 저장하지 않고 글자만 뽑는다.
 *
 * PDF·docx·pptx 는 Tika 가 내용으로 형식을 가리므로 여기서는 **서비스가 지키는 규칙**
 * (확장자·용량·상한·공백 정리)을 텍스트 파일로 확인한다.
 */
class MaterialExtractionServiceTest {

    private val service = MaterialExtractionService(policy(maxUploadMb = 10))

    @Test
    fun `텍스트 파일에서 본문을 뽑고 빈 줄·들여쓰기를 접는다`() {
        // 문서에서 뽑은 글자는 빈 줄이 많다 — 그대로 보내면 토큰만 먹는다
        val file = file("수업노트.txt", "  스택은 LIFO 다.  \n\n\n   큐는 FIFO 다.   \n\n")

        val result = service.extract(file)

        assertThat(result.text).isEqualTo("스택은 LIFO 다.\n큐는 FIFO 다.")
        assertThat(result.fileName).isEqualTo("수업노트.txt")
        assertThat(result.charCount).isEqualTo(result.text.length)
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun `상한을 넘으면 앞에서부터 자르고 잘렸다고 알린다`() {
        val long = "가".repeat(AiGenerateRequest.MATERIAL_MAX_LENGTH + 500)

        val result = service.extract(file("긴자료.txt", long))

        assertThat(result.text).hasSize(AiGenerateRequest.MATERIAL_MAX_LENGTH)
        assertThat(result.charCount).isEqualTo(AiGenerateRequest.MATERIAL_MAX_LENGTH)
        // 화면이 "뒷부분은 빠졌어요"를 말할 수 있어야 한다 — 조용히 자르면 왜 범위가 좁은지 알 수 없다
        assertThat(result.truncated).isTrue()
    }

    @Test
    fun `받지 않는 확장자는 무엇을 올릴 수 있는지 알려 준다`() {
        assertThatThrownBy { service.extract(file("강의영상.mp4", "무엇이든")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("PDF")
    }

    @Test
    fun `용량을 넘기면 상한을 숫자로 알려 준다`() {
        val small = MaterialExtractionService(policy(maxUploadMb = 1))
        val twoMb = MockMultipartFile("file", "큰자료.txt", "text/plain", ByteArray(2 * 1024 * 1024) { 'a'.code.toByte() })

        assertThatThrownBy { small.extract(twoMb) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("1MB")
    }

    @Test
    fun `글자가 없는 파일은 스캔 문서일 수 있다고 알려 준다`() {
        // 스캔 이미지 PDF 가 이 경로다 — "실패"로만 말하면 왜 안 되는지 알 수 없다
        assertThatThrownBy { service.extract(file("빈자료.txt", "   \n\n   ")) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("스캔")
    }

    private fun file(name: String, content: String) =
        MockMultipartFile("file", name, "text/plain", content.toByteArray())

    private fun policy(maxUploadMb: Int) = PolicyProperties(
        entryFeeMin = 100,
        entryFeeMax = 10000,
        chargeAmountMin = 1000,
        chargeAmountMax = 1000000,
        settlementMinAmount = 10000,
        aiFreeLimit = 5,
        materialMaxUploadMb = maxUploadMb,
        essayAnalysisFreeLimit = 5,
        essayAnalysisCoinCost = 100,
        ratingWindowHours = 24,
        hostEarningRate = 0.8,
        guestRetentionDays = 7,
        autoAdvanceDelaySeconds = 5,
    )
}
