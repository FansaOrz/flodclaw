package com.foldledger.presentation.ledger

/**
 * 从未分类账单文案中筛出可学习的关键词候选（细粒度拆分）。
 */
object KeywordSuggestions {
    private val generic = setOf(
        "支付宝商户", "支付宝", "微信支付", "微信转账", "微信", "未命名商户", "未知商户",
        "手动记账", "收款", "付款", "支付成功", "已支付", "交易成功", "商户", "消费",
        "支出", "收入", "转账", "红包", "余额", "银行", "储蓄卡", "信用卡", "借记卡",
        "订单", "账单", "交易", "成功", "完成", "通知", "提醒", "服务", "平台",
        "财付通", "零钱", "支付", "扫码", "二维码", "商家", "用户", "客户", "先生", "女士",
        "有限公司", "股份", "公司", "专营店", "旗舰店", "官方", "旗舰",
    )

    /** 拆词时剥掉的后缀，保留更短品牌名 */
    private val merchantSuffixes = listOf(
        "有限责任公司", "股份有限公司", "有限公司", "分公司", "专营店", "旗舰店",
        "体验店", "便利店", "超市", "餐厅", "饭店", "食堂", "门店", "分店", "总店", "店",
    )

    private val splitRegex = Regex("""[\s|/\\|、，,.\-—_·•【】\[\]（）()「」『』《》<>]+""")
    private val chineseRun = Regex("""[\u4e00-\u9fff]{2,}""")
    private val latinToken = Regex("""[A-Za-z][A-Za-z0-9]{1,15}""")
    private val mixedToken = Regex("""[\u4e00-\u9fffA-Za-z][\u4e00-\u9fffA-Za-z0-9·&\-]{1,15}""")
    private val parenContent = Regex("""[（(]([^）)]{1,20})[）)]""")

    /**
     * @param occupiedElsewhere 已被其他分类占用的关键词（小写）
     * @param alreadyInTarget 目标分类已有关键词（小写）
     */
    fun suggest(
        merchant: String,
        rawText: String?,
        note: String,
        occupiedElsewhere: Set<String>,
        alreadyInTarget: Set<String>,
    ): List<SuggestedKeyword> {
        val blocked = occupiedElsewhere.map { it.lowercase() }.toMutableSet()
        blocked += alreadyInTarget.map { it.lowercase() }
        blocked += generic.map { it.lowercase() }

        val candidates = linkedMapOf<String, Boolean>() // value = 默认勾选

        fun consider(raw: String?, defaultSelected: Boolean) {
            val t = raw?.trim().orEmpty()
            if (t.length < 2 || t.length > 24) return
            if (looksLikeNoise(t)) return
            val key = t.lowercase()
            if (key in blocked) return
            if (key in generic.map { it.lowercase() }) return
            if (!candidates.containsKey(t)) {
                candidates[t] = defaultSelected
            } else if (defaultSelected) {
                candidates[t] = true
            }
        }

        fun considerAllSplits(text: String, preferSelected: Boolean) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            consider(trimmed, preferSelected)

            parenContent.findAll(trimmed).forEach { m ->
                consider(m.groupValues[1], false)
                considerAllSplits(m.groupValues[1], false)
            }
            val withoutParen = trimmed.replace(parenContent, "").trim()
            if (withoutParen != trimmed) consider(withoutParen, preferSelected)

            splitRegex.split(withoutParen.ifBlank { trimmed })
                .map { it.trim() }
                .filter { it.length >= 2 }
                .forEach { part ->
                    consider(part, preferSelected && part == withoutParen)
                    emitNgrams(part) { consider(it, false) }
                    stripSuffixChain(part).forEach { consider(it, preferSelected && it.length >= 2) }
                }

            chineseRun.findAll(trimmed).forEach { m ->
                emitNgrams(m.value) { consider(it, false) }
            }
            latinToken.findAll(trimmed).forEach { m ->
                consider(m.value, false)
            }
            mixedToken.findAll(trimmed).forEach { m ->
                consider(m.value, false)
            }
        }

        val cleanMerchant = merchant.trim()
        if (cleanMerchant.isNotEmpty() && cleanMerchant.lowercase() !in generic.map { it.lowercase() }) {
            considerAllSplits(cleanMerchant, preferSelected = true)
            // 默认勾选：完整商户、去括号、去后缀后的主名
            consider(cleanMerchant, true)
            val core = stripSuffixChain(
                cleanMerchant.replace(parenContent, "").trim(),
            ).firstOrNull() ?: cleanMerchant.replace(parenContent, "").trim()
            if (core.length >= 2) consider(core, true)
        }

        listOf(note, rawText.orEmpty()).forEach { blob ->
            if (blob.isBlank()) return@forEach
            // 原文只取较短片段，避免整段通知进候选
            blob.lineSequence()
                .flatMap { splitRegex.split(it).asSequence() }
                .map { it.trim() }
                .filter { it.length in 2..16 }
                .take(40)
                .forEach { considerAllSplits(it, preferSelected = false) }
        }

        return candidates.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Boolean>> { it.value }
                    .thenByDescending { score(it.key) }
                    .thenByDescending { it.key.length },
            )
            .take(24)
            .map { SuggestedKeyword(text = it.key, selectedByDefault = it.value) }
    }

    /** 2～4 字中文滑动窗口 + 原文本身 */
    private fun emitNgrams(text: String, emit: (String) -> Unit) {
        val s = text.trim()
        if (s.length < 2) return
        emit(s)
        val onlyCn = s.filter { it in '\u4e00'..'\u9fff' }
        if (onlyCn.length in 2..12) {
            for (n in 2..minOf(4, onlyCn.length)) {
                for (i in 0..onlyCn.length - n) {
                    emit(onlyCn.substring(i, i + n))
                }
            }
        }
    }

    private fun stripSuffixChain(text: String): List<String> {
        val out = mutableListOf<String>()
        var cur = text.trim()
        out += cur
        var changed: Boolean
        do {
            changed = false
            for (suffix in merchantSuffixes) {
                if (cur.length > suffix.length + 1 && cur.endsWith(suffix)) {
                    cur = cur.removeSuffix(suffix).trim()
                    if (cur.length >= 2) {
                        out += cur
                        changed = true
                    }
                    break
                }
            }
        } while (changed)
        return out.distinct()
    }

    private fun score(text: String): Int {
        var s = 0
        if (text.any { it in '\u4e00'..'\u9fff' }) s += 3
        if (text.length in 2..6) s += 2
        if (text.length in 7..10) s += 1
        return s
    }

    private fun looksLikeNoise(text: String): Boolean {
        if (text.all { it.isDigit() || it == '.' || it == ',' }) return true
        if (Regex("""^\d+(\.\d+)?元?$""").matches(text)) return true
        if (Regex("""^\d{4}[-/年]\d{1,2}""").containsMatchIn(text)) return true
        if (text.length == 1) return true
        if (Regex("""^(第?\d+[笔单号]|单号|订单号)$""").matches(text)) return true
        return false
    }
}

data class SuggestedKeyword(
    val text: String,
    val selectedByDefault: Boolean,
)
