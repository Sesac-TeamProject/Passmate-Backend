package kr.passmate.report.service

import kr.passmate.common.exception.BusinessException
import kr.passmate.common.exception.ErrorCode
import kr.passmate.feedback.service.AnswerFeedbackQueryService
import kr.passmate.room.service.RoomQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

/** 내보낼 파일 형식. PDF 는 아직 지원하지 않는다 — 요청이 오면 무엇이 되는지 알려 준다. */
enum class ExportFormat {
    CSV,
    ;

    companion object {
        fun of(raw: String): ExportFormat = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "지금은 csv 로만 내보낼 수 있습니다.")
    }
}

/** 내보낸 파일 한 벌. 파일명은 서버가 정한다 — 클라이언트가 붙이면 방마다 제각각이 된다. */
data class ExportedFile(
    val fileName: String,
    val contentType: String,
    val content: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * 방 리포트 내보내기 (US5). 세션 요약 · 문항별 · 학생별을 한 파일에 담는다.
 *
 * 호스트가 엑셀로 여는 파일이라 **BOM 을 붙인다** — 없으면 한글이 깨져서 열린다.
 */
@Service
@Transactional(readOnly = true)
class ReportExportService(
    private val roomQueryService: RoomQueryService,
    private val materialsLoader: SessionMaterialsLoader,
    private val answerFeedbackQueryService: AnswerFeedbackQueryService,
) {

    fun export(roomId: Long, hostUserId: Long, format: String): ExportedFile {
        ExportFormat.of(format)

        val room = roomQueryService.getRoom(roomId)
        room.verifyHost(hostUserId)
        val m = materialsLoader.load(room)
        val analyzed = answerFeedbackQueryService.viewsOf(m.answers.map { it.id })

        val csv = buildString {
            appendRow("PassMate 방 리포트")
            appendRow("방 번호", roomId.toString())
            appendRow("제목", room.title)
            appendRow("상태", room.status.name)
            appendRow("시작", room.startedAt?.format(TIMESTAMP).orEmpty())
            appendRow("종료", room.endedAt?.format(TIMESTAMP).orEmpty())
            appendRow("참가자 수", m.participants.size.toString())
            appendRow("문항 수", m.sessionQuestions.size.toString())

            val gradedAll = m.answers.filter { it.isCorrect != null }
            appendRow("평균 정답률(%)", format2(percent(gradedAll.count { it.isCorrect == true }, gradedAll.size)))
            appendRow(
                "평균 점수",
                format2(
                    if (m.participants.isEmpty()) 0.0
                    else m.participants.sumOf { m.scoreOf(it.id) }.toDouble() / m.participants.size,
                ),
            )
            appendRow("AI 분석 건수", answerFeedbackQueryService.countAnalyzed(m.answers.map { it.id }).toString())

            appendLine()
            appendRow("문항별")
            appendRow("순번", "유형", "문항", "배점", "제출", "정답", "정답률(%)", "AI 분석")
            m.sessionQuestions.forEach { sq ->
                val answers = m.answersBySessionQuestion[sq.id].orEmpty()
                val graded = answers.filter { it.isCorrect != null }
                val correct = graded.count { it.isCorrect == true }
                val question = m.questionsById[sq.questionId]
                appendRow(
                    sq.orderNo.toString(),
                    question?.type?.name.orEmpty(),
                    question?.content.orEmpty(),
                    question?.points?.toString().orEmpty(),
                    answers.size.toString(),
                    correct.toString(),
                    format2(percent(correct, graded.size)),
                    answers.count { analyzed[it.id]?.analysis != null }.toString(),
                )
            }

            appendLine()
            appendRow("학생별")
            appendRow("순위", "닉네임", "점수", "정답", "제출", "정답률(%)")
            m.participants
                .sortedBy { m.rankOf(it.id) }
                .forEach { participant ->
                    val answers = m.answersOf(participant.id)
                    val graded = answers.filter { it.isCorrect != null }
                    appendRow(
                        m.rankOf(participant.id).toString(),
                        participant.nickname,
                        m.scoreOf(participant.id).toString(),
                        m.correctCountOf(participant.id).toString(),
                        answers.size.toString(),
                        format2(percent(m.correctCountOf(participant.id), graded.size)),
                    )
                }
        }

        return ExportedFile(
            fileName = "passmate-room-$roomId-report.csv",
            contentType = "text/csv;charset=UTF-8",
            // 엑셀은 BOM 이 없으면 UTF-8 을 못 알아보고 한글을 깬다
            content = (BOM + csv).toByteArray(Charsets.UTF_8),
        )
    }

    private fun StringBuilder.appendRow(vararg cells: String) {
        // CSV 는 개행도 줄 구분자라 \r\n 으로 맞춘다(RFC 4180) — 엑셀이 그걸 기대한다
        append(cells.joinToString(",") { escape(it) }).append("\r\n")
    }

    /** 쉼표·따옴표·줄바꿈이 든 값은 따옴표로 감싸고 내부 따옴표는 두 번 쓴다(RFC 4180). */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun percent(part: Int, whole: Int): Double = if (whole == 0) 0.0 else part * 100.0 / whole

    private fun format2(value: Double): String = String.format("%.2f", value)

    private companion object {
        const val BOM = "﻿"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
