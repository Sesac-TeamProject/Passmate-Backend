package kr.passmate.common.export

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode

/** 내보낼 파일 형식. PDF 는 아직 지원하지 않는다 — 요청이 오면 무엇이 되는지 알려 준다. */
enum class ExportFormat {
    CSV,
    ;

    companion object {
        fun of(raw: String): ExportFormat = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "지금은 csv 로만 내보낼 수 있습니다.")
    }
}

/** 내보낸 파일 한 벌. 파일명은 서버가 정한다 — 클라이언트가 붙이면 화면마다 제각각이 된다. */
data class ExportedFile(
    val fileName: String,
    val contentType: String,
    val content: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * CSV 한 장을 만드는 공통 규칙.
 *
 * 리포트도 정산도 **엑셀로 여는 파일**이라 같은 두 가지를 지켜야 한다 —
 * BOM 이 없으면 한글이 깨지고, 줄바꿈이 CRLF 가 아니면 일부 엑셀이 한 줄로 읽는다.
 */
class CsvBuilder {

    private val sb = StringBuilder()

    fun row(vararg cells: String) = apply {
        sb.append(cells.joinToString(",") { escape(it) }).append(CRLF)
    }

    fun blank() = apply { sb.append(CRLF) }

    /** 엑셀은 BOM 이 없으면 UTF-8 을 못 알아보고 한글을 깬다. */
    fun toBytes(): ByteArray = (BOM + sb).toByteArray(Charsets.UTF_8)

    /** RFC 4180 — 쉼표·따옴표·줄바꿈이 있으면 감싸고, 안의 따옴표는 두 번 쓴다. */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    companion object {
        const val BOM = "﻿"
        const val CSV_CONTENT_TYPE = "text/csv; charset=UTF-8"
        private const val CRLF = "\r\n"
    }
}
