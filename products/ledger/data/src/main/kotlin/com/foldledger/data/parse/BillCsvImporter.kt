package com.foldledger.data.parse

import com.foldledger.domain.model.AccountType
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.ParsedPayment
import com.foldledger.domain.util.Fingerprint
import com.foldledger.domain.util.MoneyFormat
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BillImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String> = emptyList(),
    val detail: String = "",
) {
    fun userMessage(): String {
        if (detail.isNotBlank() && imported == 0 && skipped == 0) return detail
        val base = "账单导入完成：成功 ${imported} 条，跳过 ${skipped} 条。"
        val err = if (errors.isNotEmpty()) "\n\n说明：${errors.joinToString("；")}" else ""
        val tip = when {
            imported > 0 -> "\n\n请到「流水」页查看（未开全自动时在待确认里）。"
            else -> ""
        }
        return base + err + tip + if (detail.isNotBlank()) "\n\n$detail" else ""
    }
}

/**
 * Import official WeChat / Alipay bill CSV exports (after user downloads from app).
 */
@Singleton
class BillCsvImporter @Inject constructor() {

    fun parse(csvText: String): Pair<CaptureSource, List<ParsedPayment>> {
        val normalized = csvText.removePrefix("\uFEFF")
        val lines = normalized.lines()
        val headerIndex = lines.indexOfFirst { line ->
            val l = line.lowercase()
            (l.contains("交易时间") || l.contains("付款时间") || l.contains("交易创建")) &&
                (l.contains("金额") || l.contains("收/支") || l.contains("收支"))
        }.takeIf { it >= 0 } ?: lines.indexOfFirst { it.contains(",") }.coerceAtLeast(0)

        val headerLine = lines.getOrNull(headerIndex).orEmpty()
        val isWechat = headerLine.contains("交易单号") || headerLine.contains("交易类型") ||
            normalized.contains("微信支付") || normalized.contains("微信账单")
        val isAlipay = headerLine.contains("交易号") || headerLine.contains("商家订单号") ||
            normalized.contains("支付宝") || normalized.contains("alipay")
        val source = when {
            isAlipay && !isWechat -> CaptureSource.ALIPAY_IMPORT
            else -> CaptureSource.WECHAT_IMPORT
        }
        val accountType = if (source == CaptureSource.ALIPAY_IMPORT) AccountType.ALIPAY else AccountType.WECHAT
        val pkg = if (source == CaptureSource.ALIPAY_IMPORT) {
            PaymentNotificationParser.PKG_ALIPAY
        } else {
            PaymentNotificationParser.PKG_WECHAT
        }

        val headers = splitCsv(headerLine).map { it.trim().trim('"') }
        val colExact = { names: List<String> ->
            names.firstNotNullOfOrNull { n -> headers.indexOfFirst { it == n }.takeIf { it >= 0 } }
        }
        val colFuzzy = { names: List<String> ->
            colExact(names) ?: names.firstNotNullOfOrNull { n ->
                headers.indexOfFirst { it.contains(n) }.takeIf { it >= 0 }
            }
        }
        val timeIdx = colFuzzy(listOf("交易时间", "付款时间", "交易创建"))
            ?: colExact(listOf("最近修改时间"))
        val amountIdx = colFuzzy(listOf("金额(元)", "金额（元）", "金额"))
        val dirIdx = colExact(listOf("收/支", "收支")) ?: colFuzzy(listOf("收/支", "收支"))
        val peerIdx = colFuzzy(listOf("交易对方", "商户名称")) ?: colExact(listOf("对方"))
        val goodsIdx = colFuzzy(listOf("商品名称", "商品", "备注"))
        val typeIdx = colExact(listOf("交易类型"))
        val statusIdx = colFuzzy(listOf("当前状态", "交易状态", "资金状态"))
        val idIdx = colFuzzy(listOf("交易单号", "交易号", "商家订单号", "商户单号"))

        val payments = mutableListOf<ParsedPayment>()
        for (i in (headerIndex + 1) until lines.size) {
            val row = splitCsv(lines[i])
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            fun cell(idx: Int?): String = idx?.let { row.getOrNull(it)?.trim()?.trim('"').orEmpty() }.orEmpty()

            val status = cell(statusIdx)
            if (status.isNotBlank() && status !in okStatus) continue

            val amountRaw = cell(amountIdx).removePrefix("+").removePrefix("-")
            val amount = MoneyFormat.yuanTextToFen(amountRaw) ?: continue
            if (amount <= 0) continue

            val type = cell(typeIdx)
            val dirRaw = cell(dirIdx)
            val direction = when {
                dirRaw.contains("收") || dirRaw.contains("入") -> MoneyDirection.INCOME
                dirRaw.contains("支") || dirRaw.contains("出") -> MoneyDirection.EXPENSE
                type.contains("转账") && (status.contains("已收") || dirRaw.contains("收")) -> MoneyDirection.INCOME
                amountRaw.startsWith("-") || cell(amountIdx).startsWith("-") -> MoneyDirection.EXPENSE
                else -> MoneyDirection.EXPENSE
            }

            val peer = cell(peerIdx).ifBlank { cell(goodsIdx) }.ifBlank { "账单导入" }
            val goods = cell(goodsIdx)
            val merchant = peer.take(40)
            val timeRaw = cell(timeIdx).ifBlank {
                // 表头对不上时，从整行里抠日期时间
                Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}([日\s]+\d{1,2}:\d{2}(:\d{2})?)?""")
                    .find(lines[i])?.value.orEmpty()
            }
            val time = parseTime(timeRaw)
                ?: parseTime(cell(timeIdx))
            if (time == null) {
                // 没有可靠时间就跳过，避免全部变成「今天」
                continue
            }
            val orderId = cell(idIdx)
            val id = orderId.ifBlank { "$peer|$amount|$time" }
            // 有官方单号时指纹不依赖时间，便于修时间后重新导入去重
            val fp = if (orderId.isNotBlank()) {
                Fingerprint.of("$pkg-import", amount, orderId, 0L, windowSec = 1L shl 20)
            } else {
                Fingerprint.of("$pkg-import", amount, id, time, windowSec = 1)
            }
            val raw = listOf(type, peer, goods, lines[i]).filter { it.isNotBlank() }.joinToString(" | ").take(300)

            payments += ParsedPayment(
                amountFen = amount,
                merchant = merchant,
                direction = direction,
                suggestedAccountType = accountType,
                source = source,
                packageName = pkg,
                rawTitle = type.ifBlank { "账单导入" },
                rawText = raw,
                capturedAt = time,
                fingerprint = fp,
                externalId = orderId.ifBlank { null },
            )
        }
        return source to payments
    }

    private fun parseTime(raw: String): Long? {
        val s = raw.trim().trim('"')
            .replace('年', '-').replace('月', '-').replace('日', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (s.isBlank()) return null

        // Excel 日期序列号（xlsx 数值单元格常见），如 45432.520833
        s.toDoubleOrNull()?.let { serial ->
            if (serial in 20000.0..80000.0) return excelSerialToEpochMs(serial)
        }

        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy-M-d HH:mm:ss",
            "yyyy/M/d HH:mm:ss",
            "yyyy-M-d HH:mm",
            "yyyy/M/d HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy-M-d",
            "yyyy/M/d",
        )
        val tz = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.CHINA).apply {
                    isLenient = false
                    timeZone = tz
                }.parse(s)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    /** Excel 1900 date system：序列日从 1899-12-30 起算。 */
    private fun excelSerialToEpochMs(serial: Double): Long {
        val wholeDays = kotlin.math.floor(serial).toInt()
        val fraction = serial - wholeDays
        val cal = java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone("Asia/Shanghai"),
            Locale.CHINA,
        )
        cal.clear()
        cal.set(1899, java.util.Calendar.DECEMBER, 30, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DATE, wholeDays)
        val millisInDay = (fraction * 24.0 * 60.0 * 60.0 * 1000.0).toLong()
        return cal.timeInMillis + millisInDay
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out += cur.toString()
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    companion object {
        private val okStatus = setOf(
            "支付成功", "已转账", "已收钱", "对方已收钱", "已收款", "交易成功",
            "成功", "支出", "收入", "已完成", "还款成功", "充值成功",
        )
    }
}
