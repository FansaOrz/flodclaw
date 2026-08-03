package com.foldpods.bluetooth.aacp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.foldpods.domain.BatteryLevels
import com.foldpods.domain.BatterySnapshot
import com.foldpods.domain.ConnectionStatus
import com.foldpods.domain.LidState
import com.foldpods.domain.ListeningMode
import com.foldpods.domain.SnapshotSource
import com.foldsuite.core.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * AACP over L2CAP PSM 0x1001。
 *
 * Socket 创建对齐 CAPod（BluetoothSocketSettings / HiddenApiBypass）。
 * 连接成功后发送 handshake + notification enable，并解析电量推送。
 */
@Singleton
class AacpTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val socketFactory: L2capSocketFactory,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: BluetoothSocket? = null
    private var connectedAddress: String? = null
    private var readerJob: Job? = null

    private val battery = MutableStateFlow<BatterySnapshot?>(null)

    val isConnected: Boolean get() = socket?.isConnected == true
    val address: String? get() = connectedAddress
    fun observeBattery(): StateFlow<BatterySnapshot?> = battery.asStateFlow()

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    suspend fun probe(address: String): ConnectionStatus = withContext(Dispatchers.IO) {
        val device = adapter?.getRemoteDevice(address)
            ?: return@withContext ConnectionStatus(lastError = "蓝牙不可用")
        val opened = openL2capSocket(device)
        if (opened.socket == null) {
            return@withContext ConnectionStatus(
                l2capAvailable = false,
                l2capNeedsRootHint = true,
                lastError = opened.error ?: CREATE_HINT,
            )
        }
        try {
            opened.socket.connectWithTimeout(CONNECT_TIMEOUT_SEC.seconds)
            opened.socket.close()
            ConnectionStatus(l2capAvailable = true, l2capNeedsRootHint = false, lastError = null)
        } catch (e: Exception) {
            runCatching { opened.socket.close() }
            ConnectionStatus(
                l2capAvailable = true, // socket 已能创建（CAPod 同路径）
                l2capNeedsRootHint = false,
                lastError = "L2CAP 已创建但 connect 超时/失败：${e.message ?: e.javaClass.simpleName}。" +
                    "常见原因：CAPod 占用 PSM 0x1001、耳机未出盒、或 ACL 瞬时繁忙。",
            )
        }
    }

    suspend fun connect(address: String): Outcome<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            disconnectLocked()
            val device = adapter?.getRemoteDevice(address)
                ?: return@withContext Outcome.Err("蓝牙不可用")
            val opened = openL2capSocket(device)
            val sock = opened.socket
                ?: return@withContext Outcome.Err(opened.error ?: CREATE_HINT)
            try {
                // CAPod：connect 在独立线程 + 5s 超时，避免永远卡在 INIT
                sock.connectWithTimeout(CONNECT_TIMEOUT_SEC.seconds)
                sendSessionSetup(sock)
                socket = sock
                connectedAddress = address
                readerJob = scope.launch { readLoop(sock, address) }
                Log.i(TAG, "AACP connected to $address")
                Outcome.Ok(Unit)
            } catch (e: Exception) {
                disconnectLocked()
                runCatching { sock.close() }
                Outcome.Err(
                    "AACP 连接失败：${e.message ?: e.javaClass.simpleName}。" +
                        "请确认：1) 已 force-stop CAPod（同 PSM 互斥）2) 耳机盒开盖/已出盒 3) A2DP 已连。",
                    e,
                )
            }
        }
    }

    suspend fun disconnect() = mutex.withLock {
        withContext(Dispatchers.IO) { disconnectLocked() }
    }

    suspend fun setListeningMode(mode: ListeningMode): Outcome<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val sock = socket ?: return@withContext Outcome.Err("AACP 未连接（需先成功建立 L2CAP）")
            val payload = when (mode) {
                ListeningMode.OFF -> CMD_MODE_OFF
                ListeningMode.TRANSPARENCY -> CMD_MODE_TRANSPARENCY
                ListeningMode.NOISE_CANCELLATION -> CMD_MODE_ANC
                ListeningMode.ADAPTIVE -> CMD_MODE_ADAPTIVE
            }
            try {
                sock.outputStream.write(payload)
                sock.outputStream.flush()
                Outcome.Ok(Unit)
            } catch (e: IOException) {
                Outcome.Err("写入降噪模式失败", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openL2capSocket(device: android.bluetooth.BluetoothDevice): OpenedSocket {
        return try {
            OpenedSocket(socketFactory.createSocket(device, PSM))
        } catch (e: Exception) {
            val root = generateSequence<Throwable>(e) { it.cause }.last()
            OpenedSocket(
                null,
                "$CREATE_HINT 细节：${root.javaClass.simpleName}: ${root.message}",
            )
        }
    }

    private fun sendSessionSetup(sock: BluetoothSocket) {
        sock.outputStream.write(HANDSHAKE)
        sock.outputStream.flush()
        for (packet in NOTIFICATION_ENABLE) {
            sock.outputStream.write(packet)
            sock.outputStream.flush()
        }
        sock.outputStream.write(INIT_EXT)
        sock.outputStream.flush()
    }

    private suspend fun readLoop(sock: BluetoothSocket, address: String) {
        val buf = ByteArray(2048)
        try {
            while (scope.isActive && sock.isConnected) {
                val n = try {
                    sock.inputStream.read(buf)
                } catch (_: IOException) {
                    break
                }
                if (n <= 0) break
                val frame = buf.copyOf(n)
                val levels = AapBatteryDecoder.tryDecodeBattery(frame) ?: continue
                val left = levels.leftPercent ?: levels.singlePercent
                val right = levels.rightPercent ?: levels.singlePercent
                val leftCh = if (levels.leftPercent != null) levels.leftCharging else levels.singleCharging
                val rightCh = if (levels.rightPercent != null) levels.rightCharging else levels.singleCharging
                battery.value = BatterySnapshot(
                    address = address,
                    modelId = 0,
                    modelLabel = "AACP",
                    battery = BatteryLevels(
                        leftPercent = left,
                        rightPercent = right,
                        casePercent = levels.casePercent,
                        leftCharging = leftCh,
                        rightCharging = rightCh,
                        caseCharging = levels.caseCharging,
                    ),
                    lid = LidState.UNKNOWN,
                    rssi = 0,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    source = SnapshotSource.AACP,
                )
                Log.i(
                    TAG,
                    "AACP battery L=${left} R=${right} case=${levels.casePercent}",
                )
            }
        } finally {
            Log.i(TAG, "AACP read loop ended for $address")
        }
    }

    private suspend fun disconnectLocked() {
        readerJob?.cancel()
        readerJob?.cancelAndJoin()
        readerJob = null
        runCatching { socket?.close() }
        socket = null
        connectedAddress = null
        battery.value = null
    }

    /** 对齐 CAPod AapConnection.connectCancellable：阻塞 connect 放线程里，带超时可取消。 */
    private suspend fun BluetoothSocket.connectWithTimeout(timeout: kotlin.time.Duration) {
        val result = CompletableDeferred<Result<Unit>>()
        val cancelled = AtomicBoolean(false)
        val thread = Thread(
            {
                val outcome = runCatching { connect() }
                result.complete(outcome)
                if (cancelled.get() && outcome.isSuccess) {
                    runCatching { close() }
                }
            },
            "FoldPods-AACP-connect",
        ).apply {
            isDaemon = true
            start()
        }
        try {
            withTimeout(timeout) {
                result.await().getOrThrow()
            }
        } catch (e: Exception) {
            cancelled.set(true)
            runCatching { close() }
            thread.interrupt()
            throw e
        }
    }

    private data class OpenedSocket(
        val socket: BluetoothSocket?,
        val error: String? = null,
    )

    companion object {
        private const val TAG = "FoldPods.AACP"
        const val PSM = 0x1001
        private const val CONNECT_TIMEOUT_SEC = 8
        private const val CREATE_HINT =
            "无法创建 AACP 所需的 L2CAP（PSM 0x1001）。" +
                "FoldPods 已对齐 CAPod 的 BluetoothSocketSettings / HiddenApiBypass 路径；" +
                "若仍失败，再考虑 OEM 限制或耳机未配对。"

        private val HANDSHAKE = byteArrayOf(
            0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        private val NOTIFICATION_ENABLE = listOf(
            byteArrayOf(
                0x04, 0x00, 0x04, 0x00, 0x0f, 0x00,
                0xff.toByte(), 0xff.toByte(), 0xef.toByte(), 0xff.toByte(),
            ),
            byteArrayOf(
                0x04, 0x00, 0x04, 0x00, 0x0f, 0x00,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            ),
        )
        private val INIT_EXT = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x4d, 0x00, 0xd7.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        private val CMD_MODE_OFF = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x01, 0x00, 0x00, 0x00,
        )
        private val CMD_MODE_ANC = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x02, 0x00, 0x00, 0x00,
        )
        private val CMD_MODE_TRANSPARENCY = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x03, 0x00, 0x00, 0x00,
        )
        private val CMD_MODE_ADAPTIVE = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x04, 0x00, 0x00, 0x00,
        )
    }
}
