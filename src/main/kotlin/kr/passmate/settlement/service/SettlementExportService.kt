package kr.passmate.settlement.service

import kr.passmate.common.export.CsvBuilder
import kr.passmate.common.export.ExportFormat
import kr.passmate.common.export.ExportedFile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 정산 내역 내보내기 (FR-056).
 *
 * 호스트가 세무 자료로 쓰는 파일이라 **금액을 가공하지 않고 그대로** 적는다 —
 * 화면에서 반올림해 보여준 값과 파일이 다르면 대조가 안 된다.
 */
@Service
@Transactional(readOnly = true)
class SettlementExportService(
    private val hostEarningQueryService: HostEarningQueryService,
) {

    fun export(hostUserId: Long, format: String, today: LocalDate = LocalDate.now()): ExportedFile {
        ExportFormat.of(format)

        val rows = hostEarningQueryService.rowsOf(hostUserId)
        val csv = CsvBuilder()
            .row("PassMate 정산 내역")
            .row("내보낸 날짜", today.toString())
            .row("건수", rows.size.toString())
            .row("정산액 합계", rows.sumOf { it.net }.toString())
            .blank()
            .row("적립일", "방 번호", "방 이름", "참여 인원", "참가비 총액", "수수료", "정산액", "상태")

        rows.forEach {
            csv.row(
                it.earnedAt.format(TIMESTAMP),
                it.roomId.toString(),
                it.roomTitle,
                it.participantCount.toString(),
                it.gross.toString(),
                it.platformFee.toString(),
                it.net.toString(),
                it.status.name,
            )
        }

        return ExportedFile(
            fileName = "passmate-settlement-$today.csv",
            contentType = CsvBuilder.CSV_CONTENT_TYPE,
            content = csv.toBytes(),
        )
    }

    private companion object {
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
