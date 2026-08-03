package com.foldledger.data.parse

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads WeChat / Alipay bill exports: CSV text, .xlsx, or zip containing CSV/xlsx.
 */
@Singleton
class BillFileLoader @Inject constructor() {

    sealed class LoadResult {
        data class Ok(val csvText: String, val hint: String = "") : LoadResult()
        data class Err(val message: String) : LoadResult()
    }

    fun load(bytes: ByteArray, displayName: String? = null, mime: String? = null): LoadResult {
        if (bytes.isEmpty()) return LoadResult.Err("文件为空，请重新选择账单文件。")
        val name = displayName.orEmpty().lowercase()
        val mimeL = mime.orEmpty().lowercase()

        // OLE Compound Document (.xls legacy) — not supported
        if (bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        ) {
            return LoadResult.Err(
                "这是旧版 Excel（.xls）。请用 WPS/Excel 打开后另存为「CSV UTF-8」或「.xlsx」再导入。",
            )
        }

        // ZIP container: .xlsx or email zip with csv inside
        if (isZip(bytes) || name.endsWith(".xlsx") || name.endsWith(".zip") ||
            mimeL.contains("spreadsheetml") || mimeL.contains("zip")
        ) {
            return loadFromZip(bytes, name)
        }

        // Prefer decoding as text/csv
        val text = decodeText(bytes)
        if (looksLikeBillCsv(text)) {
            return LoadResult.Ok(text)
        }
        if (looksBinaryGarbage(text)) {
            return LoadResult.Err(
                "无法识别该文件（可能是加密包或非账单格式）。请导入微信/支付宝导出的 CSV 或 Excel（.xlsx）。",
            )
        }
        // Still try — BillCsvImporter may find a header deeper in the file
        return LoadResult.Ok(text, hint = "已按文本读取；若结果为 0 条，请确认文件是账单导出。")
    }

    fun load(input: InputStream, displayName: String? = null, mime: String? = null): LoadResult {
        return load(input.readBytes(), displayName, mime)
    }

    private fun loadFromZip(bytes: ByteArray, name: String): LoadResult {
        val entries = readZipEntries(bytes)
        if (entries.isEmpty()) {
            return LoadResult.Err("压缩包损坏或无法打开。请重新从微信/支付宝导出。")
        }

        // Prefer CSV inside zip (common for email downloads)
        val csvEntry = entries.entries.firstOrNull { (n, _) ->
            n.lowercase().endsWith(".csv") && !n.contains("__macosx")
        }
        if (csvEntry != null) {
            val text = decodeText(csvEntry.value)
            return if (text.isBlank()) {
                LoadResult.Err("压缩包内的 CSV 为空。")
            } else {
                LoadResult.Ok(text, hint = "已从压缩包读取：${csvEntry.key.substringAfterLast('/')}")
            }
        }

        // xlsx workbook
        if (entries.keys.any { it.startsWith("xl/") } || name.endsWith(".xlsx")) {
            return runCatching {
                val csv = XlsxWorkbookReader().toCsv(entries)
                if (csv.isBlank()) {
                    LoadResult.Err("Excel 工作表为空。")
                } else {
                    LoadResult.Ok(csv, hint = "已从 Excel（.xlsx）读取工作表。")
                }
            }.getOrElse {
                LoadResult.Err("读取 Excel 失败：${it.message ?: "未知错误"}。可另存为 CSV 后再试。")
            }
        }

        return LoadResult.Err(
            "压缩包里没有 CSV 或 Excel 账单。请直接选择导出的 .csv / .xlsx 文件。",
        )
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                out[entry.name] = zis.readBytes()
                zis.closeEntry()
            }
        }
        return out
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8.removePrefix("\uFEFF")
        // WeChat/Alipay sometimes ship GBK CSV
        return runCatching {
            bytes.toString(Charset.forName("GB18030")).removePrefix("\uFEFF")
        }.getOrDefault(utf8.removePrefix("\uFEFF"))
    }

    private fun looksLikeBillCsv(text: String): Boolean {
        val head = text.take(4000)
        return head.contains("交易时间") || head.contains("微信支付") ||
            head.contains("支付宝") || head.contains("交易对方") ||
            head.contains("金额")
    }

    private fun looksBinaryGarbage(text: String): Boolean {
        if (text.isEmpty()) return true
        val sample = text.take(512)
        val weird = sample.count { it.code < 9 || (it.code in 14..31) }
        return weird > sample.length / 10
    }
}

/** Minimal XLSX → CSV (first worksheet), no Apache POI. */
internal class XlsxWorkbookReader {
    fun toCsv(entries: Map<String, ByteArray>): String {
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) }.orEmpty()
        val sheetName = entries.keys
            .filter { it.matches(Regex("""xl/worksheets/sheet\d+\.xml""")) }
            .sortedWith(compareBy({ it != "xl/worksheets/sheet1.xml" }, { it }))
            .firstOrNull()
            ?: return ""
        val sheet = entries[sheetName] ?: return ""
        val rows = parseSheet(sheet, shared)
        return rows.joinToString("\n") { row ->
            row.joinToString(",") { cell -> escapeCsv(cell) }
        }
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = newParser(xml)
        val list = mutableListOf<String>()
        var inSi = false
        var inT = false
        val buf = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        inSi = true
                        buf.clear()
                    }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inT) buf.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> {
                        list += buf.toString()
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val parser = newParser(xml)
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var inC = false
        var cellType = ""
        var inV = false
        var inT = false
        var valueBuf = StringBuilder()
        var maxCol = -1
        var colIndex = -1

        fun colFromRef(ref: String): Int {
            var n = 0
            for (ch in ref) {
                if (!ch.isLetter()) break
                n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
            }
            return n - 1
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = mutableListOf()
                        maxCol = -1
                    }
                    "c" -> {
                        inC = true
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        colIndex = if (ref.isNotBlank()) colFromRef(ref) else maxCol + 1
                        valueBuf = StringBuilder()
                    }
                    "v" -> if (inC) inV = true
                    "t" -> if (inC) inT = true
                }
                XmlPullParser.TEXT -> {
                    if (inV || inT) valueBuf.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = valueBuf.toString()
                        val text = when (cellType) {
                            "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                            "inlineStr", "str" -> raw
                            else -> raw
                        }
                        while (currentRow.size <= colIndex) currentRow.add("")
                        if (colIndex >= 0) currentRow[colIndex] = text
                        maxCol = maxOf(maxCol, colIndex)
                        inC = false
                        cellType = ""
                        colIndex = -1
                    }
                    "row" -> {
                        if (currentRow.any { it.isNotBlank() }) {
                            rows += currentRow.toList()
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun newParser(xml: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser().apply {
            setInput(ByteArrayInputStream(xml), "UTF-8")
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
