package com.foldpods.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldpods.domain.AirPodsRepository
import com.foldpods.domain.BatterySnapshot
import com.foldpods.domain.BleScanStats
import com.foldpods.domain.ClassicHeadset
import com.foldpods.domain.ConnectionStatus
import com.foldpods.domain.EarDetectionState
import com.foldpods.domain.FoldPodsPreferences
import com.foldpods.domain.FoldPodsPrefsStore
import com.foldpods.domain.LidState
import com.foldpods.domain.ListeningMode
import com.foldpods.domain.SnapshotSource
import com.foldpods.domain.resolveBatterySnapshot
import com.foldpods.domain.resolveSelectedAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoldPodsUiState(
    val primary: BatterySnapshot? = null,
    val nearby: List<BatterySnapshot> = emptyList(),
    val classicConnected: List<ClassicHeadset> = emptyList(),
    val selectedAddress: String? = null,
    val linkedBleAddress: String? = null,
    val batteryLinkedHeuristically: Boolean = false,
    val scanStats: BleScanStats = BleScanStats(),
    val connection: ConnectionStatus = ConnectionStatus(),
    val ear: EarDetectionState = EarDetectionState(),
    val listeningMode: ListeningMode? = null,
    val prefs: FoldPodsPreferences = FoldPodsPreferences(),
    val statusMessage: String? = null,
    val showLidPopup: Boolean = false,
)

@HiltViewModel
class FoldPodsViewModel @Inject constructor(
    private val repository: AirPodsRepository,
    private val prefsStore: FoldPodsPrefsStore,
) : ViewModel() {

    /** 仅用户手动点选；未点选时由「已连接蓝牙 > 记忆 > 最新广播」自动决定。 */
    private val userPickedAddress = MutableStateFlow<String?>(null)

    private val deviceSlice = combine(
        repository.observeNearby(),
        repository.observeClassicConnected(),
        userPickedAddress,
        prefsStore.observe(),
        repository.observeScanStats(),
    ) { nearby, classic, userPicked, prefs, stats ->
        DeviceSlice(nearby, classic, userPicked, prefs, stats)
    }

    private val statusSlice = combine(
        repository.observeConnection(),
        repository.observeEarDetection(),
        repository.observeListeningMode(),
        repository.observeAacpBattery(),
    ) { connection, ear, mode, aacpBattery ->
        StatusSlice(connection, ear, mode, aacpBattery)
    }

    val uiState: StateFlow<FoldPodsUiState> = combine(deviceSlice, statusSlice) { d, s ->
        val selected = resolveSelectedAddress(
            userPicked = d.userPicked,
            nearby = d.nearby,
            classicConnected = d.classic,
            lastBondAddress = d.prefs.lastBondAddress,
        )
        val primary = resolveBatterySnapshot(
            selectedAddress = selected,
            nearby = d.nearby,
            linkedBleAddress = d.prefs.linkedBleAddress,
            aacpBattery = s.aacpBattery,
        )
        val exactMatch = primary?.address.equals(selected, ignoreCase = true)
        val linkedMatch = primary?.address.equals(d.prefs.linkedBleAddress, ignoreCase = true)
        val aacpMatch = primary?.source == SnapshotSource.AACP
        FoldPodsUiState(
            primary = primary,
            nearby = d.nearby.sortedWith(
                compareByDescending<BatterySnapshot> { snap ->
                    snap.address.equals(d.prefs.linkedBleAddress, true) ||
                        d.classic.any { it.connected && it.address.equals(snap.address, true) }
                }.thenByDescending { it.address.equals(selected, true) }
                    .thenByDescending { it.lid == LidState.OPEN }
                    .thenByDescending { it.rssi },
            ),
            classicConnected = d.classic.filter { it.connected },
            selectedAddress = selected,
            linkedBleAddress = d.prefs.linkedBleAddress,
            batteryLinkedHeuristically = primary != null && !exactMatch && !linkedMatch && !aacpMatch,
            scanStats = d.stats,
            connection = s.connection,
            ear = s.ear,
            listeningMode = s.mode,
            prefs = d.prefs,
            showLidPopup = primary?.lid == LidState.OPEN,
            statusMessage = s.connection.lastError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldPodsUiState())

    init {
        viewModelScope.launch { repository.startBleScan() }
        viewModelScope.launch {
            combine(
                repository.observeClassicConnected(),
                prefsStore.observe(),
                userPickedAddress,
            ) { classic, prefs, userPicked ->
                Triple(classic, prefs, userPicked)
            }.collect { (classic, prefs, userPicked) ->
                if (userPicked != null) return@collect
                val live = classic.firstOrNull { it.connected }?.address ?: return@collect
                if (!live.equals(prefs.lastBondAddress, ignoreCase = true)) {
                    prefsStore.update { it.copy(lastBondAddress = live) }
                }
            }
        }
    }

    /** 点选系统已连接设备：作为 AACP/重连目标。 */
    fun selectDevice(address: String) {
        userPickedAddress.value = address
        viewModelScope.launch {
            prefsStore.update { it.copy(lastBondAddress = address) }
        }
    }

    /** 点选附近 BLE：关联为电量来源（经典地址常不同）。 */
    fun linkBleBattery(address: String) {
        viewModelScope.launch {
            prefsStore.update { it.copy(linkedBleAddress = address) }
        }
    }

    fun connectAacp() {
        viewModelScope.launch {
            val address = uiState.value.selectedAddress
                ?: uiState.value.classicConnected.firstOrNull()?.address
                ?: return@launch
            repository.connectAacp(address)
        }
    }

    fun probeL2cap() {
        viewModelScope.launch {
            val address = uiState.value.selectedAddress
                ?: uiState.value.classicConnected.firstOrNull()?.address
                ?: return@launch
            repository.probeL2cap(address)
        }
    }

    fun setMode(mode: ListeningMode) {
        viewModelScope.launch { repository.setListeningMode(mode) }
    }

    fun cycleMode() {
        viewModelScope.launch {
            val modes = uiState.value.prefs.preferredListeningModes
            if (modes.isEmpty()) return@launch
            val current = uiState.value.listeningMode
            val idx = modes.indexOf(current).let { if (it < 0) 0 else (it + 1) % modes.size }
            repository.setListeningMode(modes[idx])
        }
    }

    fun assistReconnect() {
        viewModelScope.launch {
            val preferred = uiState.value.selectedAddress
            if (preferred != null) {
                prefsStore.update { it.copy(lastBondAddress = preferred) }
            }
            repository.assistReconnectBonded()
        }
    }

    fun updatePrefs(transform: (FoldPodsPreferences) -> FoldPodsPreferences) {
        viewModelScope.launch { prefsStore.update(transform) }
    }

    private data class DeviceSlice(
        val nearby: List<BatterySnapshot>,
        val classic: List<ClassicHeadset>,
        val userPicked: String?,
        val prefs: FoldPodsPreferences,
        val stats: BleScanStats,
    )

    private data class StatusSlice(
        val connection: ConnectionStatus,
        val ear: EarDetectionState,
        val mode: ListeningMode?,
        val aacpBattery: BatterySnapshot?,
    )
}
