package com.foldledger.capture.importing

import com.foldledger.capture.pipeline.CapturePipeline
import com.foldledger.data.db.PendingCaptureDao
import com.foldledger.data.parse.BillCsvImporter
import com.foldledger.data.parse.BillImportResult
import com.foldledger.domain.model.CaptureSource
import com.foldledger.domain.repo.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillImportUseCase @Inject constructor(
    private val importer: BillCsvImporter,
    private val pipeline: CapturePipeline,
    private val transactions: TransactionRepository,
    private val pendingDao: PendingCaptureDao,
) {
    /**
     * @param forceAutoAll 设置页「全自动」打开时，支付宝导入也会直接入库；
     * 微信账单导入始终自动确认入库。
     *
     * 合并策略：只覆盖「同交易单号 / 同指纹」的旧记录，不同时间段的历史导入予以保留。
     */
    suspend fun importCsv(csvText: String, forceAutoAll: Boolean = false): BillImportResult {
        val head = csvText.take(800)
        val looksBill = head.contains("交易时间") || head.contains("交易对方") ||
            head.contains("微信支付") || head.contains("支付宝") || head.contains("金额")
        if (!looksBill) {
            return BillImportResult(
                imported = 0,
                skipped = 0,
                detail = "未识别为微信/支付宝账单。请确认文件含「交易时间」「金额」「交易对方」等列（官方导出的 CSV/Excel）。",
            )
        }

        val (source, payments) = importer.parse(csvText)
        if (payments.isEmpty()) {
            return BillImportResult(
                imported = 0,
                skipped = 0,
                detail = "文件已读取，但没有解析到有效交易（含交易时间无法识别的行会被跳过）。请确认是官方导出的账单。",
            )
        }

        val orderIds = payments.mapNotNull { it.externalId?.takeIf { id -> id.isNotBlank() } }.toSet()
        val fingerprints = payments.map { it.fingerprint }.toSet()
        val removedTx = transactions.hardDeleteMatchingImports(orderIds, fingerprints)

        var removedPending = 0
        for (p in pendingDao.listUnresolved()) {
            val hit = orderIds.any { p.rawText.contains(it) || p.fingerprint.contains(it) } ||
                p.fingerprint in fingerprints
            if (hit) {
                pendingDao.markResolved(p.id)
                removedPending++
            }
        }

        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        var wechatAuto = 0
        var pendingCount = 0
        for (p in payments) {
            try {
                val auto = forceAutoAll || p.source == CaptureSource.WECHAT_IMPORT
                val outcome = if (auto) {
                    val id = pipeline.persistConfirmed(p)
                    if (id > 0) CapturePipeline.SubmitOutcome.IMPORTED else CapturePipeline.SubmitOutcome.FAILED
                } else {
                    pipeline.submitNow(p, notify = false)
                }
                when (outcome) {
                    CapturePipeline.SubmitOutcome.IMPORTED -> {
                        imported++
                        if (auto && p.source == CaptureSource.WECHAT_IMPORT) wechatAuto++
                        if (!auto) pendingCount++
                    }
                    CapturePipeline.SubmitOutcome.DUPLICATE -> skipped++
                    CapturePipeline.SubmitOutcome.FAILED -> {
                        skipped++
                        if (errors.size < 3) errors += "入库失败（可能缺账户）"
                    }
                }
            } catch (t: Throwable) {
                skipped++
                if (errors.size < 5) errors += (t.message ?: "error")
            }
        }
        val detail = buildString {
            when (source) {
                CaptureSource.WECHAT_IMPORT -> append("微信账单已按账单时间合并入库（其他时间段历史保留）。")
                CaptureSource.ALIPAY_IMPORT -> append(
                    if (forceAutoAll) "支付宝账单已合并入库（全自动开启）。"
                    else "支付宝账单已进入待确认（其他时间段历史保留）。",
                )
                else -> append("导入完成。")
            }
            if (removedTx > 0) append(" 替换重复 $removedTx 条。")
            if (removedPending > 0) append(" 清理待确认 $removedPending 条。")
            if (wechatAuto > 0) append(" 本次写入 $wechatAuto 条。")
            if (pendingCount > 0) append(" 待确认 $pendingCount 条。")
        }
        return BillImportResult(
            imported = imported,
            skipped = skipped,
            errors = errors,
            detail = detail,
        )
    }
}
