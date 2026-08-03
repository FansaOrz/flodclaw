package com.foldpods.bluetooth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.SparseArray
import com.foldpods.domain.BatterySnapshot
import com.foldpods.domain.BleScanStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE 邻近扫描 —— 对齐 CAPod 的成功路径：
 * 1. ScanFilter 锁定 Apple Continuity type=0x07 + length=25（官方明文电量包）
 * 2. CALLBACK_TYPE_ALL_MATCHES + LOW_LATENCY + 定期 flush
 * 3. 三星上若硬件过滤不可靠，回退到无过滤 + 软件匹配同一 mask
 */
@Singleton
class BleProximityScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val statsFlow = MutableStateFlow(BleScanStats())
    private val appleCount = AtomicLong(0)
    private val proximityCount = AtomicLong(0)

    fun observeStats(): Flow<BleScanStats> = statsFlow.asStateFlow()

    init {
        ProximityPairingParser.diagnostic = { level, message ->
            when (level) {
                "I" -> android.util.Log.i(TAG, message)
                "W" -> android.util.Log.w(TAG, message)
                else -> android.util.Log.d(TAG, message)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun observeNearby(): Flow<List<BatterySnapshot>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        appleCount.set(0)
        proximityCount.set(0)
        statsFlow.value = BleScanStats(scanning = true)

        val cache = ConcurrentHashMap<String, BatterySnapshot>()
        val offloadOk = adapter?.isOffloadedFilteringSupported == true

        fun emitFresh(now: Long = System.currentTimeMillis()) {
            val staleBefore = now - STALE_MS
            cache.entries.removeIf { it.value.updatedAtEpochMs < staleBefore }
            trySend(cache.values.sortedByDescending { it.updatedAtEpochMs })
            statsFlow.value = BleScanStats(
                scanning = true,
                applePackets = appleCount.get(),
                proximityHits = proximityCount.get(),
                nearbyCount = cache.size,
            )
        }

        fun handleResult(result: ScanResult) {
            val address = result.device?.address ?: return
            val mfg = result.scanRecord?.manufacturerSpecificData ?: return
            val appleData = mfg.manufacturer(ProximityPairingParser.APPLE_COMPANY_ID) ?: return
            appleCount.incrementAndGet()
            val snapshot = ProximityPairingParser.parseManufacturerData(
                data = appleData,
                address = address,
                rssi = result.rssi,
            )
            if (snapshot == null) {
                if (result.rssi > -70) {
                    android.util.Log.d(
                        TAG,
                        "apple near but not proximity addr=$address rssi=${result.rssi} " +
                            "hex=${appleData.take(28).joinToString("") { "%02X".format(it) }}",
                    )
                }
                emitFresh()
                return
            }
            proximityCount.incrementAndGet()
            cache[address] = snapshot
            emitFresh()
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handleResult(result)

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleResult)
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.w(TAG, "scan failed code=$errorCode")
                statsFlow.value = statsFlow.value.copy(lastError = "scanFailed=$errorCode")
            }
        }

        // CAPod ProximityPairing.getBleScanFilter()：type=0x07, length=25
        val filters = proximityScanFilters()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .apply {
                if (Build.VERSION.SDK_INT >= 23) {
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }
            }
            .build()

        // 路径 A：硬件过滤（与 CAPod 默认一致）
        val hwFilters = if (offloadOk) filters else emptyList()
        runCatching {
            scanner.startScan(hwFilters, settings, callback)
            android.util.Log.i(
                TAG,
                "BLE scan started offload=$offloadOk filters=${hwFilters.size} (CAPod-style 0x07/25)",
            )
        }.onFailure {
            android.util.Log.e(TAG, "startScan failed: ${it.message}")
            close(it)
            return@callbackFlow
        }

        // 路径 B：若硬件过滤不可用，或作为补充——无过滤 + 软件匹配（三星常见兼容项）
        val softCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // 软件路径：不过滤，直接解析（三星上 ScanFilter.matches 有时不可靠）
                handleResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleResult)
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.w(TAG, "soft scan failed code=$errorCode")
            }
        }
        val softStarted = AtomicBoolean(false)
        // 立即双路径：HW 过滤 + 无过滤软件解析（对标 CAPod troubleshooter 的 unfiltered）
        runCatching {
            scanner.startScan(null, settings, softCallback)
            softStarted.set(true)
            android.util.Log.i(TAG, "BLE unfiltered parse scan started (CAPod-compat)")
        }

        val flush = launch {
            while (isActive) {
                delay(500)
                runCatching { scanner.flushPendingScanResults(callback) }
                if (softStarted.get()) runCatching { scanner.flushPendingScanResults(softCallback) }
            }
        }

        val softFallback = launch {
            // reserved — unfiltered already started
        }

        val janitor = launch {
            while (isActive) {
                delay(5_000)
                emitFresh()
            }
        }

        awaitClose {
            flush.cancel()
            softFallback.cancel()
            janitor.cancel()
            runCatching { scanner.stopScan(callback) }
            if (softStarted.get()) runCatching { scanner.stopScan(softCallback) }
            statsFlow.value = statsFlow.value.copy(scanning = false)
        }
    }

    companion object {
        private const val STALE_MS = 25_000L
        private const val TAG = "FoldPodsBle"
        private const val PAIRING_MSG_LEN = 25 // CAPod PAIRING_MESSAGE_LENGTH
        private const val CONTINUITY_MSG_TOTAL = 27 // type + length + 25

        /** 与 CAPod ProximityPairing.getBleScanFilter() 对齐。 */
        fun proximityScanFilters(): List<ScanFilter> {
            fun filterForLength(len: Int): ScanFilter {
                val data = ByteArray(CONTINUITY_MSG_TOTAL).apply {
                    this[0] = ProximityPairingParser.TYPE_PROXIMITY.toByte()
                    this[1] = len.toByte()
                }
                val mask = ByteArray(CONTINUITY_MSG_TOTAL).apply {
                    this[0] = 1
                    this[1] = 1
                }
                return ScanFilter.Builder()
                    .setManufacturerData(ProximityPairingParser.APPLE_COMPANY_ID, data, mask)
                    .build()
            }
            // 官方 25；部分旧固件/文档出现 0x12
            return listOf(filterForLength(PAIRING_MSG_LEN), filterForLength(0x12))
        }
    }
}

private fun SparseArray<ByteArray>.manufacturer(key: Int): ByteArray? {
    val idx = indexOfKey(key)
    return if (idx >= 0) valueAt(idx) else null
}
