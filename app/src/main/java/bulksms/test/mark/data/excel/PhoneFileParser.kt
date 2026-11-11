package bulksms.test.mark.data.excel

import android.content.ContentResolver
import android.net.Uri
import bulksms.test.mark.data.sms.PhoneNormalizer
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader

data class ParseResult(
    val validRaw: List<String>,           // unique normalized numbers, in input order
    val rawValidPairs: List<Pair<String, String>>, // (rawInput, normalizedE164) for storage
    val totalRows: Int,
    val invalidCount: Int,
    val duplicateCount: Int
)

/**
 * Parses a single-column phone-number file into a ParseResult.
 * Auto-detects whether row 1 is a header.
 * Silently drops invalid + duplicate numbers.
 * Caps at MAX_ROWS to protect UI/Room from misclick on a giant file.
 */
class PhoneFileParser(private val resolver: ContentResolver) {

    fun parse(uri: Uri): ParseResult {
        val name = (resolver.queryName(uri) ?: "").lowercase()
        val isCsv = name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".txt")
        val rawCells: List<String> = if (isCsv) parseCsv(uri) else parseXlsx(uri)
        return classify(rawCells)
    }

    private fun parseXlsx(uri: Uri): List<String> {
        val cells = ArrayList<String>()
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open file" }
            WorkbookFactory.create(input).use { wb ->
                val sheet = wb.getSheetAt(0)
                outer@ for (row in sheet) {
                    if (cells.size >= MAX_ROWS) break@outer
                    val cell = row.getCell(0) ?: continue
                    val s = when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> {
                            val d = cell.numericCellValue
                            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                        }
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        CellType.FORMULA -> try { cell.stringCellValue } catch (_: Throwable) {
                            try { cell.numericCellValue.toLong().toString() } catch (_: Throwable) { "" }
                        }
                        else -> ""
                    }
                    cells += s.trim()
                }
            }
        }
        return cells
    }

    private fun parseCsv(uri: Uri): List<String> {
        val cells = ArrayList<String>()
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open file" }
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { br ->
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    if (cells.size >= MAX_ROWS) break
                    val first = line!!.split(',', '\t', ';').firstOrNull().orEmpty().trim().trim('"')
                    cells += first
                }
            }
        }
        return cells
    }

    private fun classify(cells: List<String>): ParseResult {
        if (cells.isEmpty()) return ParseResult(emptyList(), emptyList(), 0, 0, 0)

        // Header detection: if row 1 doesn't normalize to a phone, but other rows do, treat as header.
        val firstNormalizable = PhoneNormalizer.normalizeEthiopian(cells[0]) != null
        val hasHeaderHint = !firstNormalizable && cells.drop(1).any { PhoneNormalizer.normalizeEthiopian(it) != null }
        val data = if (hasHeaderHint) cells.drop(1) else cells

        val seen = HashSet<String>()
        val validRaw = ArrayList<String>()
        val pairs = ArrayList<Pair<String, String>>()
        var invalid = 0
        var duplicate = 0

        for (raw in data) {
            if (raw.isBlank()) { invalid++; continue }
            val n = PhoneNormalizer.normalizeEthiopian(raw)
            if (n == null) { invalid++; continue }
            if (!seen.add(n)) { duplicate++; continue }
            validRaw += n
            pairs += raw to n
        }

        return ParseResult(
            validRaw = validRaw,
            rawValidPairs = pairs,
            totalRows = data.size,
            invalidCount = invalid,
            duplicateCount = duplicate
        )
    }

    private fun ContentResolver.queryName(uri: Uri): String? {
        return try {
            query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val MAX_ROWS = 100_000
    }
}
