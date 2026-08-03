package com.foldpods.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSelectionTest {

    private fun snap(
        address: String,
        updated: Long = 1L,
        lid: LidState = LidState.CLOSED,
        modelId: Int = 0x1420,
        rssi: Int = -60,
    ) = BatterySnapshot(
        address = address,
        modelId = modelId,
        modelLabel = "AirPods Pro 2",
        battery = BatteryLevels(50, 50, 50),
        lid = lid,
        rssi = rssi,
        updatedAtEpochMs = updated,
    )

    @Test
    fun prefersUserPick() {
        val result = resolveSelectedAddress(
            userPicked = "AA:11",
            nearby = listOf(snap("BB:22", 9), snap("AA:11", 1)),
            classicConnected = listOf(ClassicHeadset("BB:22", "AirPods", a2dpConnected = true)),
            lastBondAddress = "CC:33",
        )
        assertEquals("AA:11", result)
    }

    @Test
    fun prefersClassicConnectedMatchingNearby() {
        val result = resolveSelectedAddress(
            userPicked = null,
            nearby = listOf(snap("OLD", 99), snap("LIVE", 1)),
            classicConnected = listOf(ClassicHeadset("LIVE", "AirPods Pro", a2dpConnected = true)),
            lastBondAddress = "OLD",
        )
        assertEquals("LIVE", result)
    }

    @Test
    fun prefersClassicAirPodsEvenIfNotInNearby() {
        val result = resolveSelectedAddress(
            userPicked = null,
            nearby = listOf(snap("BLE-ONLY", 50)),
            classicConnected = listOf(
                ClassicHeadset("CLASSIC", "Yifan's AirPods Pro", a2dpConnected = true),
            ),
            lastBondAddress = "BLE-ONLY",
        )
        assertEquals("CLASSIC", result)
    }

    @Test
    fun batteryUsesLinkedBleWhenClassicDiffers() {
        val linked = resolveBatterySnapshot(
            selectedAddress = "CLASSIC",
            nearby = listOf(snap("BLE-A", lid = LidState.CLOSED, rssi = -40), snap("BLE-B", lid = LidState.OPEN, rssi = -90)),
            linkedBleAddress = "BLE-A",
            nowEpochMs = 1000L,
        )
        assertEquals("BLE-A", linked?.address)
    }

    @Test
    fun batteryPrefersAacpOverBle() {
        val aacp = snap("CLASSIC", updated = 900L).copy(
            battery = BatteryLevels(88, 76, 40),
            source = SnapshotSource.AACP,
        )
        val result = resolveBatterySnapshot(
            selectedAddress = "CLASSIC",
            nearby = listOf(snap("BLE-A", lid = LidState.OPEN, rssi = -30)),
            linkedBleAddress = "BLE-A",
            aacpBattery = aacp,
            nowEpochMs = 1000L,
        )
        assertEquals(SnapshotSource.AACP, result?.source)
        assertEquals(88, result?.battery?.leftPercent)
    }

    @Test
    fun batteryHeuristicPrefersOpenLid() {
        val auto = resolveBatterySnapshot(
            selectedAddress = "CLASSIC",
            nearby = listOf(
                snap("WEAK-OPEN", lid = LidState.OPEN, rssi = -95),
                snap("STRONG-CLOSED", lid = LidState.CLOSED, rssi = -40),
            ),
            linkedBleAddress = null,
            nowEpochMs = 1000L,
        )
        assertEquals("WEAK-OPEN", auto?.address)
        assertTrue(auto!!.lid == LidState.OPEN)
    }

    @Test
    fun fallsBackToNewestWhenNothingConnected() {
        val result = resolveSelectedAddress(
            userPicked = null,
            nearby = listOf(snap("A", 1), snap("B", 10)),
            classicConnected = emptyList(),
            lastBondAddress = null,
        )
        assertEquals("B", result)
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(
            resolveSelectedAddress(null, emptyList(), emptyList(), null),
        )
    }
}
