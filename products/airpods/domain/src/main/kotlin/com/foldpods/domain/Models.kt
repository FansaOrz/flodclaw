package com.foldpods.domain

enum class LidState {
    UNKNOWN,
    CLOSED,
    OPEN,
}

enum class ListeningMode {
    OFF,
    TRANSPARENCY,
    NOISE_CANCELLATION,
    ADAPTIVE,
}

enum class EarPresence {
    UNKNOWN,
    IN_EAR,
    OUT_OF_EAR,
}

/**
 * BLE 邻近广播电量（约 10% 一档）。null = 未知/未佩戴侧。
 */
data class BatteryLevels(
    val leftPercent: Int?,
    val rightPercent: Int?,
    val casePercent: Int?,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false,
)

data class BatterySnapshot(
    val address: String?,
    val modelId: Int,
    val modelLabel: String,
    val battery: BatteryLevels,
    val lid: LidState,
    val colorCode: Int = 0,
    val rssi: Int = 0,
    val updatedAtEpochMs: Long,
    val source: SnapshotSource = SnapshotSource.BLE_PROXIMITY,
)

enum class SnapshotSource {
    BLE_PROXIMITY,
    AACP,
}

data class ProCapability(
    val modelId: Int,
    val label: String,
    val supportsAdaptive: Boolean,
    val supportsConversationalAwareness: Boolean,
    val supportsStemVolumeSwipe: Boolean,
    val supportsHearingAid: Boolean,
    val supportsHeartRate: Boolean,
) {
    companion object {
        // Community reverse-engineering model ids (OpenPods / CAPod / LibrePods)
        private val TABLE = mapOf(
            0x0220 to ProCapability(0x0220, "AirPods", false, false, false, false, false),
            0x0F20 to ProCapability(0x0F20, "AirPods 2", false, false, false, false, false),
            0x1320 to ProCapability(0x1320, "AirPods 3", false, false, false, false, false),
            0x1B4B to ProCapability(0x1B4B, "AirPods 4", false, false, false, false, false),
            0x1B4C to ProCapability(0x1B4C, "AirPods 4 ANC", true, false, false, false, false),
            0x0A20 to ProCapability(0x0A20, "AirPods Max", false, false, false, false, false),
            0x1F20 to ProCapability(0x1F20, "AirPods Max USB-C", false, false, false, false, false),
            0x0E20 to ProCapability(0x0E20, "AirPods Pro", false, false, false, false, false),
            0x0E21 to ProCapability(0x0E21, "AirPods Pro", false, false, false, false, false),
            0x1420 to ProCapability(0x1420, "AirPods Pro 2", true, true, true, true, false),
            0x1421 to ProCapability(0x1421, "AirPods Pro 2 (USB-C)", true, true, true, true, false),
            0x1422 to ProCapability(0x1422, "AirPods Pro 2", true, true, true, true, false),
            0x1520 to ProCapability(0x1520, "AirPods Pro 3", true, true, true, true, true),
            // Wireshark/acble little-endian style ids seen in the wild
            0x2002 to ProCapability(0x2002, "AirPods", false, false, false, false, false),
            0x200A to ProCapability(0x200A, "AirPods Max", false, false, false, false, false),
            0x200E to ProCapability(0x200E, "AirPods Pro", false, false, false, false, false),
            0x200F to ProCapability(0x200F, "AirPods 2", false, false, false, false, false),
            0x2013 to ProCapability(0x2013, "AirPods 3", false, false, false, false, false),
            0x2014 to ProCapability(0x2014, "AirPods Pro 2", true, true, true, true, false),
            0x2019 to ProCapability(0x2019, "AirPods Pro 2 (USB-C)", true, true, true, true, false),
            0x2024 to ProCapability(0x2024, "AirPods Pro 3", true, true, true, true, true),
        )

        fun isKnownModel(modelId: Int): Boolean = TABLE.containsKey(modelId)

        fun forModelId(modelId: Int): ProCapability =
            TABLE[modelId] ?: ProCapability(
                modelId = modelId,
                label = "未知 Apple 音频 (0x%04X)".format(modelId),
                supportsAdaptive = false,
                supportsConversationalAwareness = false,
                supportsStemVolumeSwipe = false,
                supportsHearingAid = false,
                supportsHeartRate = false,
            )
    }
}

data class ConnectionStatus(
    val aacpConnected: Boolean = false,
    val l2capAvailable: Boolean = false,
    val l2capNeedsRootHint: Boolean = false,
    val lastError: String? = null,
)

/** 系统经典蓝牙（A2DP/HFP）当前已连接的耳机。 */
data class ClassicHeadset(
    val address: String,
    val name: String?,
    val a2dpConnected: Boolean = false,
    val hfpConnected: Boolean = false,
    /** 系统报告的整机电量 0–100；-1 / null = 未知（左右耳分立需 BLE/AACP）。 */
    val systemBatteryPercent: Int? = null,
) {
    val connected: Boolean get() = a2dpConnected || hfpConnected
}

data class BleScanStats(
    val scanning: Boolean = false,
    val applePackets: Long = 0,
    val proximityHits: Long = 0,
    val nearbyCount: Int = 0,
    val lastError: String? = null,
)

data class EarDetectionState(
    val left: EarPresence = EarPresence.UNKNOWN,
    val right: EarPresence = EarPresence.UNKNOWN,
)

data class FoldPodsPreferences(
    val pauseOnRemove: Boolean = true,
    val conversationalAwareness: Boolean = false,
    val headGestures: Boolean = false,
    val advancedModeEnabled: Boolean = false,
    val lastBondAddress: String? = null,
    /** 与经典蓝牙地址不同的 BLE 邻近广播地址，用于电量显示。 */
    val linkedBleAddress: String? = null,
    val preferredListeningModes: List<ListeningMode> = listOf(
        ListeningMode.NOISE_CANCELLATION,
        ListeningMode.TRANSPARENCY,
        ListeningMode.OFF,
    ),
)

interface AirPodsRepository {
    fun observeNearby(): kotlinx.coroutines.flow.Flow<List<BatterySnapshot>>
    fun observePrimary(): kotlinx.coroutines.flow.Flow<BatterySnapshot?>
    fun observeAacpBattery(): kotlinx.coroutines.flow.Flow<BatterySnapshot?>
    fun observeClassicConnected(): kotlinx.coroutines.flow.Flow<List<ClassicHeadset>>
    fun observeScanStats(): kotlinx.coroutines.flow.Flow<BleScanStats>
    fun observeConnection(): kotlinx.coroutines.flow.Flow<ConnectionStatus>
    fun observeEarDetection(): kotlinx.coroutines.flow.Flow<EarDetectionState>
    fun observeListeningMode(): kotlinx.coroutines.flow.Flow<ListeningMode?>
    suspend fun startBleScan()
    suspend fun stopBleScan()
    suspend fun connectAacp(address: String): com.foldsuite.core.Outcome<Unit>
    suspend fun disconnectAacp()
    suspend fun setListeningMode(mode: ListeningMode): com.foldsuite.core.Outcome<Unit>
    suspend fun probeL2cap(address: String): ConnectionStatus
    suspend fun assistReconnectBonded(): com.foldsuite.core.Outcome<String>
}

interface FoldPodsPrefsStore {
    fun observe(): kotlinx.coroutines.flow.Flow<FoldPodsPreferences>
    suspend fun update(transform: (FoldPodsPreferences) -> FoldPodsPreferences)
}
