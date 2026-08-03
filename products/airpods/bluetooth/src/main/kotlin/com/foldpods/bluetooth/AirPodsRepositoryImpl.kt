package com.foldpods.bluetooth

import com.foldpods.bluetooth.aacp.AacpTransport
import com.foldpods.bluetooth.ble.BleProximityScanner
import com.foldpods.bluetooth.reconnect.BondReconnectHelper
import com.foldpods.bluetooth.reconnect.ClassicConnectionMonitor
import com.foldpods.domain.AirPodsRepository
import com.foldpods.domain.BatterySnapshot
import com.foldpods.domain.BleScanStats
import com.foldpods.domain.ClassicHeadset
import com.foldpods.domain.ConnectionStatus
import com.foldpods.domain.EarDetectionState
import com.foldpods.domain.EarPresence
import com.foldpods.domain.FoldPodsPrefsStore
import com.foldpods.domain.LidState
import com.foldpods.domain.ListeningMode
import com.foldpods.domain.looksLikeAirPodsName
import com.foldpods.domain.resolveBatterySnapshot
import com.foldpods.domain.resolveSelectedAddress
import com.foldsuite.core.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirPodsRepositoryImpl @Inject constructor(
    private val bleScanner: BleProximityScanner,
    private val aacp: AacpTransport,
    private val reconnectHelper: BondReconnectHelper,
    private val classicMonitor: ClassicConnectionMonitor,
    private val prefsStore: FoldPodsPrefsStore,
) : AirPodsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private var statsJob: Job? = null

    private val nearby = MutableStateFlow<List<BatterySnapshot>>(emptyList())
    private val classicConnected = MutableStateFlow<List<ClassicHeadset>>(emptyList())
    private val scanStats = MutableStateFlow(BleScanStats())
    private val connection = MutableStateFlow(ConnectionStatus())
    private val ear = MutableStateFlow(EarDetectionState())
    private val listeningMode = MutableStateFlow<ListeningMode?>(null)
    private var lastOpenLidAddress: String? = null

    init {
        scope.launch {
            classicMonitor.observeConnected().collectLatest { list ->
                classicConnected.value = list
                maybeAutoConnectAacp(list)
            }
        }
    }

    override fun observeNearby(): Flow<List<BatterySnapshot>> = nearby.asStateFlow()

    override fun observeAacpBattery(): Flow<BatterySnapshot?> = aacp.observeBattery()

    override fun observeClassicConnected(): Flow<List<ClassicHeadset>> = classicConnected.asStateFlow()

    override fun observeScanStats(): Flow<BleScanStats> = scanStats.asStateFlow()

    override fun observePrimary(): Flow<BatterySnapshot?> =
        combine(
            nearby,
            classicConnected,
            prefsStore.observe(),
            aacp.observeBattery(),
        ) { list, classic, prefs, aacpSnap ->
            val selected = resolveSelectedAddress(
                userPicked = null,
                nearby = list,
                classicConnected = classic,
                lastBondAddress = prefs.lastBondAddress,
            )
            resolveBatterySnapshot(
                selectedAddress = selected,
                nearby = list,
                linkedBleAddress = prefs.linkedBleAddress,
                aacpBattery = aacpSnap,
            )
        }

    override fun observeConnection(): Flow<ConnectionStatus> = connection.asStateFlow()

    override fun observeEarDetection(): Flow<EarDetectionState> = ear.asStateFlow()

    override fun observeListeningMode(): Flow<ListeningMode?> = listeningMode.asStateFlow()

    override suspend fun startBleScan() {
        if (scanJob?.isActive == true) return
        statsJob?.cancel()
        statsJob = scope.launch {
            bleScanner.observeStats().collectLatest { scanStats.value = it }
        }
        scanJob = scope.launch {
            bleScanner.observeNearby().collectLatest { list ->
                nearby.value = list
                val prefs = prefsStore.observe().first()
                val preferred = resolveSelectedAddress(
                    userPicked = null,
                    nearby = list,
                    classicConnected = classicConnected.value,
                    lastBondAddress = prefs.lastBondAddress,
                )
                // Prefer live classic connection into prefs so notifications follow the active headset
                val liveClassic = classicConnected.value.firstOrNull { it.connected }?.address
                if (liveClassic != null && !liveClassic.equals(prefs.lastBondAddress, true)) {
                    prefsStore.update { it.copy(lastBondAddress = liveClassic) }
                }
                val open = list.firstOrNull { snap ->
                    snap.lid == LidState.OPEN &&
                        (preferred == null || snap.address.equals(preferred, ignoreCase = true))
                }
                if (open != null && open.address != null && open.address != lastOpenLidAddress) {
                    lastOpenLidAddress = open.address
                    reconnectHelper.assistReconnect(open.address)
                    if (preferred == null && liveClassic == null) {
                        prefsStore.update { it.copy(lastBondAddress = open.address) }
                    }
                }
            }
        }
    }

    override suspend fun stopBleScan() {
        scanJob?.cancel()
        scanJob = null
        statsJob?.cancel()
        statsJob = null
    }

    override suspend fun connectAacp(address: String): Outcome<Unit> {
        val result = aacp.connect(address)
        connection.value = connection.value.copy(
            aacpConnected = result is Outcome.Ok,
            l2capAvailable = if (result is Outcome.Ok) true else connection.value.l2capAvailable,
            l2capNeedsRootHint = if (result is Outcome.Ok) false else connection.value.l2capNeedsRootHint,
            lastError = (result as? Outcome.Err)?.message,
        )
        if (result is Outcome.Ok) {
            prefsStore.update { it.copy(lastBondAddress = address) }
        }
        return result
    }

    override suspend fun disconnectAacp() {
        aacp.disconnect()
        connection.value = connection.value.copy(aacpConnected = false)
        listeningMode.value = null
    }

    private var autoAacpAttemptedFor: String? = null

    /** 经典蓝牙已连接时自动尝试 AACP（CAPod 同款主路径）。 */
    private fun maybeAutoConnectAacp(classic: List<ClassicHeadset>) {
        val target = classic.firstOrNull { it.connected && looksLikeAirPodsName(it.name) }
            ?: classic.firstOrNull { it.connected }
        if (target == null) {
            autoAacpAttemptedFor = null
            return
        }
        if (aacp.isConnected && aacp.address.equals(target.address, ignoreCase = true)) return
        if (autoAacpAttemptedFor.equals(target.address, ignoreCase = true)) return
        autoAacpAttemptedFor = target.address
        scope.launch {
            // 稍等 ACL/A2DP 稳定；CAPod 也会在失败后退避重试
            kotlinx.coroutines.delay(1_500)
            if (aacp.isConnected) return@launch
            var last: Outcome<Unit> = connectAacp(target.address)
            if (last is Outcome.Ok) return@launch
            kotlinx.coroutines.delay(2_000)
            if (!aacp.isConnected) {
                last = connectAacp(target.address)
            }
            if (last is Outcome.Err) {
                // 允许用户手动再点「连接 AACP」
                autoAacpAttemptedFor = null
            }
        }
    }

    override suspend fun setListeningMode(mode: ListeningMode): Outcome<Unit> {
        val result = aacp.setListeningMode(mode)
        if (result is Outcome.Ok) listeningMode.value = mode
        else connection.value = connection.value.copy(lastError = (result as Outcome.Err).message)
        return result
    }

    override suspend fun probeL2cap(address: String): ConnectionStatus {
        val status = aacp.probe(address)
        connection.value = connection.value.copy(
            l2capAvailable = status.l2capAvailable,
            l2capNeedsRootHint = status.l2capNeedsRootHint,
            lastError = status.lastError,
        )
        return status
    }

    override suspend fun assistReconnectBonded(): Outcome<String> {
        val preferred = prefsStore.observe().first().lastBondAddress
            ?: classicConnected.value.firstOrNull { it.connected }?.address
        return reconnectHelper.assistReconnect(preferred)
    }

    /** P2 stub: ear detection updates come from AACP packets when reader is added. */
    fun publishEarDetection(left: EarPresence, right: EarPresence) {
        ear.value = EarDetectionState(left, right)
    }
}
