package com.foldledger.domain.util

import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.DuplicatePair
import com.foldledger.domain.model.TransactionWithMeta
import java.util.Locale
import kotlin.math.abs

/**
 * 全局疑似重复筛查：同额、同收支方向、时间接近、商户相似。
 * 用于「实时抓取 + 账单导入」等指纹撞不上的叠记场景。
 */
object DuplicateMatcher {
    private val genericMerchants = setOf(
        "支付宝商户", "支付宝", "微信支付", "微信转账", "微信", "未命名商户", "未知商户",
        "手动记账", "收款", "付款", "商户", "消费", "转账",
    ).map { normalize(it) }.toSet()

    /** 默认：24 小时内 */
    const val DEFAULT_WINDOW_MS: Long = 24L * 60 * 60 * 1000

    /** 泛化商户名只在更短窗口内配对，降低误报 */
    private const val GENERIC_WINDOW_MS: Long = 10L * 60 * 1000

    fun normalize(merchant: String): String =
        merchant.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")

    fun merchantsSimilar(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val shorter = if (na.length <= nb.length) na else nb
        val longer = if (na.length <= nb.length) nb else na
        if (shorter.length >= 2 && longer.contains(shorter)) return true
        val prefix = commonPrefixLen(na, nb)
        if (prefix >= 4) return true
        // 中文品牌：双方都含同一段 ≥2 字的连续中文
        val brandsA = chineseRuns(na)
        val brandsB = chineseRuns(nb)
        return brandsA.any { ba -> ba.length >= 2 && brandsB.any { bb -> bb.contains(ba) || ba.contains(bb) } }
    }

    fun findPairs(
        items: List<TransactionWithMeta>,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): List<DuplicatePair> {
        val active = items.filter { it.transaction.deletedAt == null }
            .sortedBy { it.transaction.happenedAt }
        if (active.size < 2) return emptyList()

        val candidates = mutableListOf<ScoredPair>()
        val byKey = active.groupBy { "${it.transaction.direction.name}|${it.transaction.amountFen}" }
        for ((_, group) in byKey) {
            if (group.size < 2) continue
            for (i in group.indices) {
                for (j in i + 1 until group.size) {
                    val left = group[i]
                    val right = group[j]
                    val dt = abs(left.transaction.happenedAt - right.transaction.happenedAt)
                    if (dt > windowMs) break // group sorted by time
                    val genA = isGeneric(left.transaction.merchant)
                    val genB = isGeneric(right.transaction.merchant)
                    if (genA && genB && dt > GENERIC_WINDOW_MS) continue
                    if ((genA || genB) && !merchantsSimilar(left.transaction.merchant, right.transaction.merchant)) {
                        // 一方泛化：要求另一方商户出现在对方原文/备注里，或短窗内
                        val hay = listOf(
                            right.transaction.merchant,
                            right.transaction.rawText,
                            right.transaction.note,
                            left.transaction.merchant,
                            left.transaction.rawText,
                            left.transaction.note,
                        ).joinToString("\n")
                        val brand = listOf(left.transaction.merchant, right.transaction.merchant)
                            .map { normalize(it) }
                            .filter { it.length >= 2 && it !in genericMerchants }
                            .maxByOrNull { it.length }
                        if (brand == null || !normalize(hay).contains(brand)) {
                            if (dt > GENERIC_WINDOW_MS) continue
                        }
                    } else if (!merchantsSimilar(left.transaction.merchant, right.transaction.merchant)) {
                        // 商户字面不相似时，看原文是否互含品牌
                        if (!crossTextHit(left, right)) continue
                    }
                    val score = scorePair(left, right, dt)
                    val reason = buildReason(left, right, dt)
                    candidates += ScoredPair(left, right, score, reason, dt)
                }
            }
        }

        // 贪心：时间差小、跨源优先；每个 id 只进一对
        candidates.sortWith(
            compareByDescending<ScoredPair> { it.score }
                .thenBy { it.dt },
        )
        val used = mutableSetOf<Long>()
        val result = mutableListOf<DuplicatePair>()
        for (c in candidates) {
            val idA = c.a.transaction.id
            val idB = c.b.transaction.id
            if (idA in used || idB in used) continue
            used += idA
            used += idB
            // 展示时：导入/实时放前面更直观
            val (first, second) = orderForDisplay(c.a, c.b)
            result += DuplicatePair(first = first, second = second, reason = c.reason)
        }
        return result
    }

    private fun isGeneric(merchant: String): Boolean = normalize(merchant) in genericMerchants

    private fun crossTextHit(a: TransactionWithMeta, b: TransactionWithMeta): Boolean {
        val na = normalize(a.transaction.merchant)
        val nb = normalize(b.transaction.merchant)
        if (na.length < 2 && nb.length < 2) return false
        val hayA = normalize(
            listOf(a.transaction.merchant, a.transaction.rawText, a.transaction.note).joinToString(""),
        )
        val hayB = normalize(
            listOf(b.transaction.merchant, b.transaction.rawText, b.transaction.note).joinToString(""),
        )
        if (na.length >= 2 && hayB.contains(na)) return true
        if (nb.length >= 2 && hayA.contains(nb)) return true
        return false
    }

    private fun scorePair(a: TransactionWithMeta, b: TransactionWithMeta, dt: Long): Int {
        var s = 100
        if (dt <= 5 * 60 * 1000L) s += 40
        else if (dt <= 60 * 60 * 1000L) s += 20
        if (isCrossSource(a.transaction.source, b.transaction.source)) s += 50
        if (normalize(a.transaction.merchant) == normalize(b.transaction.merchant)) s += 30
        return s
    }

    private fun isCrossSource(a: CaptureSource, b: CaptureSource): Boolean {
        fun kind(s: CaptureSource): String = when (s) {
            CaptureSource.WECHAT_IMPORT, CaptureSource.ALIPAY_IMPORT -> "import"
            CaptureSource.MANUAL -> "manual"
            else -> "live"
        }
        return kind(a) != kind(b)
    }

    private fun buildReason(a: TransactionWithMeta, b: TransactionWithMeta, dt: Long): String {
        val mins = (dt / 60_000L).coerceAtLeast(0)
        val timePart = when {
            mins < 1 -> "1 分钟内"
            mins < 60 -> "${mins} 分钟内"
            else -> "${mins / 60} 小时内"
        }
        val cross = if (isCrossSource(a.transaction.source, b.transaction.source)) "·跨来源" else ""
        return "同额·商户相似·$timePart$cross"
    }

    private fun orderForDisplay(
        a: TransactionWithMeta,
        b: TransactionWithMeta,
    ): Pair<TransactionWithMeta, TransactionWithMeta> {
        fun rank(s: CaptureSource): Int = when (s) {
            CaptureSource.MANUAL -> 0
            CaptureSource.WECHAT_IMPORT, CaptureSource.ALIPAY_IMPORT -> 2
            else -> 1
        }
        return if (rank(a.transaction.source) <= rank(b.transaction.source)) a to b else b to a
    }

    private fun commonPrefixLen(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun chineseRuns(s: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in '\u4e00'..'\u9fff') sb.append(ch)
            else if (sb.isNotEmpty()) {
                out += sb.toString()
                sb.clear()
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private data class ScoredPair(
        val a: TransactionWithMeta,
        val b: TransactionWithMeta,
        val score: Int,
        val reason: String,
        val dt: Long,
    )
}
