package kr.passmate.question.service

import kr.passmate.common.config.PolicyProperties
import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.question.dto.AiGenerateRequest
import kr.passmate.question.dto.MaterialExtractResponse
import org.apache.tika.exception.WriteLimitReachedException
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * 강의자료 파일에서 **본문 글자만** 뽑는다 (W-03 "강의자료 첨부").
 *
 * 선생님이 가진 자료는 대부분 PDF·워드·PPT 라 붙여넣기로는 쓸 수 없었다.
 * 파일을 받아 텍스트만 꺼내고 [AiGenerateRequest.material] 로 넘긴다 —
 * **파일 자체는 저장하지 않는다.** 저장하면 저작권·보관 정책이 따라붙는데
 * 우리가 필요한 것은 출제 범위를 좁힐 글자뿐이다.
 */
@Service
class MaterialExtractionService(
    private val policy: PolicyProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun extract(file: MultipartFile): MaterialExtractResponse {
        val fileName = file.originalFilename?.takeIf { it.isNotBlank() } ?: "첨부파일"
        verifyAccepted(fileName)
        verifySize(file)

        val text = normalize(parse(file, fileName))
        if (text.isBlank()) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "파일에서 읽을 수 있는 글자가 없습니다. 스캔 이미지로 된 문서는 글자를 뽑을 수 없어요.",
            )
        }

        // 계약 상한을 넘으면 앞에서부터 자른다 — 뒤를 버렸다는 것을 화면이 알려 줄 수 있게 함께 돌려준다
        val limit = AiGenerateRequest.MATERIAL_MAX_LENGTH
        return MaterialExtractResponse(
            fileName = fileName,
            text = text.take(limit),
            charCount = minOf(text.length, limit),
            truncated = text.length > limit,
        )
    }

    private fun verifyAccepted(fileName: String) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in ACCEPTED_EXTENSIONS) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "PDF · 워드(docx) · 파워포인트(pptx) · 텍스트 파일만 올릴 수 있습니다.",
            )
        }
    }

    private fun verifySize(file: MultipartFile) {
        val maxBytes = policy.materialMaxUploadMb * 1024L * 1024L
        if (file.size > maxBytes) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "강의자료는 ${policy.materialMaxUploadMb}MB 까지 올릴 수 있습니다.",
            )
        }
    }

    /**
     * Tika 는 확장자가 아니라 **내용**으로 형식을 가린다 — 이름만 바꾼 파일에 속지 않는다.
     *
     * 본문 길이 상한을 파서에 직접 걸어 둔다. 무제한으로 두면 수백 쪽짜리 PDF 한 건이
     * 메모리를 통째로 먹는데, 우리가 쓰는 것은 앞부분 [AiGenerateRequest.MATERIAL_MAX_LENGTH] 자뿐이다.
     */
    private fun parse(file: MultipartFile, fileName: String): String {
        // 잘라 낸 것을 truncated 로 알리려면 상한보다 넉넉히 읽어야 한다
        val handler = BodyContentHandler(AiGenerateRequest.MATERIAL_MAX_LENGTH * 2)
        return try {
            file.inputStream.use { AutoDetectParser().parse(it, handler, Metadata(), ParseContext()) }
            handler.toString()
        } catch (e: WriteLimitReachedException) {
            // 상한까지 읽은 것은 이미 handler 에 담겨 있다 — 실패가 아니라 정상 종료다
            log.debug("강의자료 본문이 상한을 넘어 앞부분만 읽었다 file={}", fileName)
            handler.toString()
        } catch (e: Exception) {
            log.warn("강의자료 추출 실패 file={} size={}", fileName, file.size, e)
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "파일을 읽지 못했습니다. 손상되었거나 암호가 걸린 파일인지 확인해 주세요.",
            )
        }
    }

    /** 문서에서 뽑은 글자는 빈 줄·들여쓰기가 많다. 토큰만 먹으므로 접어서 보낸다 */
    private fun normalize(raw: String): String =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    private companion object {
        val ACCEPTED_EXTENSIONS = setOf("pdf", "docx", "pptx", "txt", "md")
    }
}
