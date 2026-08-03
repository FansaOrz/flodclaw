package com.foldledger.capture.pipeline

import com.foldledger.capture.notify.CaptureAlertNotifier
import com.foldledger.data.db.AccountDao
import com.foldledger.domain.model.AccountType
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.model.MoneyDirection
import com.foldledger.domain.model.ParsedPayment
import com.foldledger.domain.model.PendingCapture
import com.foldledger.domain.model.Transaction
import com.foldledger.domain.repo.CategoryRepository
import com.foldledger.domain.repo.PendingCaptureRepository
import com.foldledger.domain.repo.SettingsRepository
import com.foldledger.domain.repo.TransactionRepository
import com.foldledger.domain.util.Fingerprint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapturePipeline @Inject constructor(
    private val transactions: TransactionRepository,
    private val pending: PendingCaptureRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountDao,
    private val settings: SettingsRepository,
    private val alerts: CaptureAlertNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // replay=1：保活服务晚于抓取启动时，也能收到最近一笔待确认
    private val _confirmEvents = MutableSharedFlow<ParsedPayment>(
        replay = 1,
        extraBufferCapacity = 16,
    )
    val confirmEvents: SharedFlow<ParsedPayment> = _confirmEvents.asSharedFlow()

    fun submit(parsed: ParsedPayment) {
        scope.launch {
            submitNow(parsed, notify = true)
        }
    }

    /**
     * @return IMPORTED / DUPLICATE / FAILED
     */
    suspend fun submitNow(parsed: ParsedPayment, notify: Boolean = true): SubmitOutcome {
        Log.i(TAG, "submit in ${parsed.source} ${parsed.amountFen} ${parsed.merchant}")
        if (transactions.getByFingerprint(parsed.fingerprint) != null) {
            Log.i(TAG, "skip duplicate tx")
            return SubmitOutcome.DUPLICATE
        }
        if (pending.existsFingerprint(parsed.fingerprint)) {
            Log.i(TAG, "skip duplicate pending")
            return SubmitOutcome.DUPLICATE
        }

        val auto = settings.autoConfirm.first()
        if (auto && parsed.amountFen > 0) {
            val id = persistConfirmed(parsed)
            return if (id > 0) SubmitOutcome.IMPORTED else SubmitOutcome.FAILED
        }
        pending.insert(
            PendingCapture(
                amountFen = parsed.amountFen,
                merchant = parsed.merchant,
                direction = parsed.direction,
                suggestedAccountType = parsed.suggestedAccountType,
                source = parsed.source,
                packageName = parsed.packageName,
                rawTitle = parsed.rawTitle,
                rawText = parsed.rawText,
                fingerprint = parsed.fingerprint,
                capturedAt = parsed.capturedAt,
            ),
        )
        if (notify) {
            alerts.notifyPending(parsed)
            _confirmEvents.emit(parsed)
        }
        return SubmitOutcome.IMPORTED
    }

    suspend fun persistConfirmed(
        parsed: ParsedPayment,
        accountIdOverride: Long? = null,
        categoryIdOverride: Long? = null,
        note: String = "",
    ): Long {
        val existing = transactions.getByFingerprint(parsed.fingerprint)
        if (existing != null && existing.deletedAt == null) {
            return existing.id
        }
        val accountId = when {
            accountIdOverride != null && accountIdOverride > 0L -> accountIdOverride
            else -> {
                accounts.getByType(parsed.suggestedAccountType.name)?.id
                    ?: accounts.listAll().firstOrNull { it.id > 0 }?.id
            }
        }
        if (accountId == null || accountId <= 0L) {
            Log.e(TAG, "persist aborted: no valid account for ${parsed.suggestedAccountType}")
            return -1
        }
        val clearRaw = settings.clearRawAfterSave.first()
        val haystack = listOf(parsed.merchant, parsed.rawTitle, parsed.rawText)
            .filter { !it.isNullOrBlank() }
            .joinToString("\n")
        val merchant = resolveMerchant(parsed.merchant, haystack)
        val categoryId = when {
            categoryIdOverride != null && categoryIdOverride > 0L -> categoryIdOverride
            else -> categories.matchByMerchant(haystack)?.id
                ?: categories.matchByMerchant(merchant)?.id
        }
        val now = System.currentTimeMillis()
        val tx = Transaction(
            id = existing?.id ?: 0L,
            amountFen = parsed.amountFen,
            direction = parsed.direction,
            merchant = merchant,
            accountId = accountId,
            categoryId = categoryId,
            happenedAt = parsed.capturedAt,
            source = parsed.source,
            rawText = if (clearRaw) null else "${parsed.rawTitle}\n${parsed.rawText}",
            fingerprint = parsed.fingerprint,
            note = note,
            deletedAt = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        Log.i(
            TAG,
            "persist ok amount=${tx.amountFen} merchant=${tx.merchant} cat=$categoryId account=$accountId",
        )
        return transactions.upsert(tx)
    }

    private fun resolveMerchant(current: String?, haystack: String): String {
        val brand = extractKnownBrand(haystack)
        val cur = current?.trim().orEmpty()
        if (brand != null && (cur.isBlank() || cur in GENERIC_MERCHANTS)) return brand
        if (cur.isNotBlank()) return cur
        return brand ?: "未命名商户"
    }

    private fun extractKnownBrand(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        KNOWN_BRANDS.forEach { if (raw.contains(it)) return it }
        return null
    }

    suspend fun confirmPending(
        pendingId: Long,
        amountFen: Long,
        merchant: String,
        direction: MoneyDirection,
        accountId: Long,
        categoryId: Long?,
        sourcePkg: String,
        source: CaptureSource,
        rawText: String,
        capturedAt: Long,
        fingerprint: String,
        note: String = "",
    ) {
        val parsed = ParsedPayment(
            amountFen = amountFen,
            merchant = merchant,
            direction = direction,
            suggestedAccountType = accounts.get(accountId)?.let {
                runCatching { com.foldledger.domain.model.AccountType.valueOf(it.type) }.getOrNull()
            } ?: com.foldledger.domain.model.AccountType.CUSTOM,
            source = source,
            packageName = sourcePkg,
            rawTitle = merchant,
            rawText = rawText,
            capturedAt = capturedAt,
            fingerprint = fingerprint.ifBlank {
                Fingerprint.of(sourcePkg, amountFen, merchant, capturedAt)
            },
        )
        persistConfirmed(parsed, accountId, categoryId, note)
        pending.markResolved(pendingId)
    }

    data class ConfirmAllResult(val confirmed: Int, val failed: Int, val skipped: Int)

    /**
     * 一键确认当前全部待确认。accountIdFor 返回账户；分类一律自动匹配。
     */
    suspend fun confirmAllPending(
        items: List<PendingCapture>,
        accountIdFor: (PendingCapture) -> Long?,
    ): ConfirmAllResult {
        var confirmed = 0
        var failed = 0
        var skipped = 0
        for (p in items) {
            val amount = p.amountFen
            if (amount == null || amount <= 0L) {
                skipped++
                continue
            }
            val accountId = accountIdFor(p)
            if (accountId == null || accountId <= 0L) {
                failed++
                continue
            }
            val source = when {
                p.source == CaptureSource.WECHAT_IMPORT ||
                    p.source == CaptureSource.ALIPAY_IMPORT ||
                    p.source == CaptureSource.BANK_SMS -> p.source
                p.packageName.contains("tencent") -> CaptureSource.WECHAT_NLS
                p.packageName.contains("eg.android.Alipay") -> CaptureSource.ALIPAY_NLS
                else -> p.source
            }
            try {
                confirmPending(
                    pendingId = p.id,
                    amountFen = amount,
                    merchant = p.merchant ?: "未知商户",
                    direction = p.direction,
                    accountId = accountId,
                    categoryId = null,
                    sourcePkg = p.packageName,
                    source = source,
                    rawText = "${p.rawTitle}\n${p.rawText}",
                    capturedAt = p.capturedAt,
                    fingerprint = p.fingerprint,
                )
                confirmed++
            } catch (t: Throwable) {
                Log.e(TAG, "confirmAll failed id=${p.id}", t)
                failed++
            }
        }
        return ConfirmAllResult(confirmed, failed, skipped)
    }

    suspend fun dismissPending(pendingId: Long) {
        pending.markResolved(pendingId)
    }

    /** 悬浮窗「记一笔」：入库并清掉对应待确认。 */
    suspend fun confirmFromOverlay(parsed: ParsedPayment): Long {
        val id = persistConfirmed(parsed)
        pending.markResolvedByFingerprint(parsed.fingerprint)
        return id
    }

    /** 悬浮窗「忽略」：仅关闭待确认，不记账。 */
    suspend fun dismissFromOverlay(parsed: ParsedPayment) {
        pending.markResolvedByFingerprint(parsed.fingerprint)
    }

    /** 通知栏「记一笔」：按指纹从 pending 取回并确认。 */
    suspend fun confirmByFingerprint(fingerprint: String): Long {
        val p = pending.getUnresolvedByFingerprint(fingerprint) ?: return -1
        val parsed = ParsedPayment(
            amountFen = p.amountFen ?: 0L,
            merchant = p.merchant.orEmpty().ifBlank { "未命名商户" },
            direction = p.direction,
            suggestedAccountType = p.suggestedAccountType ?: AccountType.CUSTOM,
            source = p.source,
            packageName = p.packageName,
            rawTitle = p.rawTitle,
            rawText = p.rawText,
            capturedAt = p.capturedAt,
            fingerprint = p.fingerprint,
        )
        return confirmFromOverlay(parsed)
    }

    suspend fun dismissByFingerprint(fingerprint: String) {
        pending.markResolvedByFingerprint(fingerprint)
    }

    enum class SubmitOutcome { IMPORTED, DUPLICATE, FAILED }

    companion object {
        private const val TAG = "FoldLedgerCapture"
        private val GENERIC_MERCHANTS = setOf(
            "支付宝商户", "支付宝", "微信支付", "微信转账", "微信", "未命名商户", "未知商户",
        )
        private val KNOWN_BRANDS = listOf(
            "滴滴出行", "滴滴", "美团外卖", "美团打车", "美团", "饿了么",
            "淘宝", "天猫", "京东", "拼多多", "高德打车", "高德", "哈啰",
            "瑞幸", "星巴克", "盒马",
        )
    }
}
