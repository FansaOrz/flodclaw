package com.foldpods.domain

/**
 * 选主设备优先级（用户未手动点选时）：
 * 1. 附近 BLE 里地址与当前 A2DP/HFP 已连接设备重合
 * 2. 系统已连接的 AirPods 类设备（即便 BLE 地址不同）
 * 3. 上次记住的地址
 * 4. 最新 BLE 广播
 */
fun resolveSelectedAddress(
    userPicked: String?,
    nearby: List<BatterySnapshot>,
    classicConnected: List<ClassicHeadset>,
    lastBondAddress: String?,
): String? {
    if (!userPicked.isNullOrBlank()) return userPicked

    val connected = classicConnected.filter { it.connected }

    val matchedNearby = nearby.firstOrNull { snap ->
        val addr = snap.address ?: return@firstOrNull false
        connected.any { it.address.equals(addr, ignoreCase = true) }
    }?.address
    if (matchedNearby != null) return matchedNearby

    val classicAirPods = connected.firstOrNull { looksLikeAirPodsName(it.name) }?.address
    if (classicAirPods != null) return classicAirPods

    val anyClassic = connected.firstOrNull()?.address
    if (anyClassic != null) return anyClassic

    if (lastBondAddress != null) return lastBondAddress

    return nearby.maxByOrNull { it.updatedAtEpochMs }?.address
}

/**
 * 电量快照解析：经典蓝牙地址常与 BLE 邻近广播地址不同。
 * 优先级：AACP（精确电量）→ 精确地址匹配 → 用户关联的 BLE → 启发式（开盖 / 已知型号 / RSSI）。
 *
 * 对抗性：CAPod 主路径是 AAP/L2CAP，不是明文 BLE 邻近包；AACP 快照应压过同址 BLE。
 */
fun resolveBatterySnapshot(
    selectedAddress: String?,
    nearby: List<BatterySnapshot>,
    linkedBleAddress: String?,
    aacpBattery: BatterySnapshot? = null,
    nowEpochMs: Long = System.currentTimeMillis(),
    staleAfterMs: Long = 25_000L,
): BatterySnapshot? {
    fun fresh(list: List<BatterySnapshot>) =
        list.filter { nowEpochMs - it.updatedAtEpochMs <= staleAfterMs }

    val aacpFresh = aacpBattery?.takeIf { nowEpochMs - it.updatedAtEpochMs <= staleAfterMs * 4 }
    if (aacpFresh != null) {
        val addr = aacpFresh.address
        if (selectedAddress == null ||
            addr.equals(selectedAddress, ignoreCase = true) ||
            linkedBleAddress.equals(addr, ignoreCase = true)
        ) {
            return aacpFresh
        }
    }

    val pool = fresh(nearby).ifEmpty { nearby }

    pool.firstOrNull {
        it.source == SnapshotSource.AACP && it.address.equals(selectedAddress, ignoreCase = true)
    }?.let { return it }

    pool.firstOrNull { it.address.equals(selectedAddress, ignoreCase = true) }?.let { return it }

    linkedBleAddress?.let { link ->
        pool.firstOrNull { it.address.equals(link, ignoreCase = true) }?.let { return it }
    }

    // 启发式：开盖优先，其次已知型号，再次信号强
    return pool.sortedWith(
        compareByDescending<BatterySnapshot> { it.lid == LidState.OPEN }
            .thenByDescending { ProCapability.isKnownModel(it.modelId) }
            .thenByDescending { it.rssi }
            .thenByDescending { it.updatedAtEpochMs },
    ).firstOrNull()
}

fun looksLikeAirPodsName(name: String?): Boolean {
    val n = name?.lowercase().orEmpty()
    return n.contains("airpods") || n.contains("airpod")
}
