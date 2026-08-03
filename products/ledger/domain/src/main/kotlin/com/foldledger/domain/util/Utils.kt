package com.foldledger.domain.util

import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale

object MoneyFormat {
    fun fenToYuan(fen: Long): String {
        val sign = if (fen < 0) "-" else ""
        val abs = kotlin.math.abs(fen)
        val yuan = abs / 100
        val rem = abs % 100
        return "%s%d.%02d".format(Locale.US, sign, yuan, rem)
    }

    fun yuanTextToFen(text: String): Long? {
        val cleaned = text.replace(",", "").replace("￥", "").replace("¥", "").trim()
        val match = Regex("""-?\d+(\.\d{1,2})?""").find(cleaned) ?: return null
        val value = match.value.toDoubleOrNull() ?: return null
        return Math.round(value * 100)
    }
}

object Fingerprint {
    fun of(
        packageName: String,
        amountFen: Long,
        merchant: String,
        timeMs: Long,
        windowSec: Long = 120,
    ): String {
        val bucket = timeMs / (windowSec * 1000)
        val normalized = merchant.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
        val raw = "$packageName|$amountFen|$normalized|$bucket"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}

object YearMonths {
    fun current(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    fun monthRange(yearMonth: String): Pair<Long, Long> {
        val parts = yearMonth.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val start = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }.timeInMillis
        return start to end
    }
}
