package bulksms.test.mark.data.excel

import android.content.ContentResolver
import android.net.Uri
import bulksms.test.mark.data.entity.Recipient
import bulksms.test.mark.util.Time
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedWriter
import java.io.OutputStreamWriter

object ResultsExporter {

    private val HEADERS = listOf(
        "rowIndex", "rawPhone", "normalizedPhone", "carrier",
        "status", "attempts", "segments", "lastError",
        "sentAt", "deliveredAt", "smsResultCode"
    )

    fun toXlsx(resolver: ContentResolver, dest: Uri, recipients: List<Recipient>) {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("results")
            val header = sheet.createRow(0)
            HEADERS.forEachIndexed { i, h -> header.createCell(i).setCellValue(h) }
            recipients.forEachIndexed { idx, r ->
                val row = sheet.createRow(idx + 1)
                row.createCell(0).setCellValue(r.rowIndex.toDouble())
                row.createCell(1).setCellValue(r.rawPhone)
                row.createCell(2).setCellValue(r.normalizedPhone)
                row.createCell(3).setCellValue(r.carrier)
                row.createCell(4).setCellValue(r.status.name)
                row.createCell(5).setCellValue(r.attempts.toDouble())
                row.createCell(6).setCellValue(r.segmentCount.toDouble())
                row.createCell(7).setCellValue(r.lastError ?: "")
                row.createCell(8).setCellValue(r.sentAt?.let { Time.iso(it) } ?: "")
                row.createCell(9).setCellValue(r.deliveredAt?.let { Time.iso(it) } ?: "")
                row.createCell(10).setCellValue(r.smsResultCode?.toDouble() ?: -1.0)
            }
            resolver.openOutputStream(dest, "w")?.use { os ->
                wb.write(os)
            } ?: error("Cannot open output for $dest")
        }
    }

    fun toCsv(resolver: ContentResolver, dest: Uri, recipients: List<Recipient>) {
        resolver.openOutputStream(dest, "w")?.use { os ->
            BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { w ->
                w.write(HEADERS.joinToString(","))
                w.newLine()
                for (r in recipients) {
                    val row = listOf(
                        r.rowIndex.toString(),
                        csvEscape(r.rawPhone),
                        csvEscape(r.normalizedPhone),
                        csvEscape(r.carrier),
                        r.status.name,
                        r.attempts.toString(),
                        r.segmentCount.toString(),
                        csvEscape(r.lastError ?: ""),
                        csvEscape(r.sentAt?.let { Time.iso(it) } ?: ""),
                        csvEscape(r.deliveredAt?.let { Time.iso(it) } ?: ""),
                        (r.smsResultCode ?: "").toString()
                    )
                    w.write(row.joinToString(","))
                    w.newLine()
                }
            }
        } ?: error("Cannot open output for $dest")
    }

    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        if (!needsQuote) return s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}
