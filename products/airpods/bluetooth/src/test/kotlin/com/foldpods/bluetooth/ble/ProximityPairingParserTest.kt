package com.foldpods.bluetooth.ble

import com.foldpods.domain.LidState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityPairingParserTest {
    @Test
    fun parsesTypicalProximityPacket() {
        // type, len, pairing, modelHi, modelLo, status, podsBatt, flagsCase, lid, color, conn
        val data = byteArrayOf(
            0x07, 0x19, 0x01,
            0x14.toByte(), 0x20, // AirPods Pro 2
            0x62,
            0xA7.toByte(), // right=10→100%, left=7→70%
            0xB3.toByte(),
            0x09, // lid open-ish
            0x02,
            0x04,
        )
        val snap = ProximityPairingParser.parseManufacturerData(
            data = data,
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -50,
            nowEpochMs = 1L,
        )
        assertNotNull(snap)
        assertEquals(0x1420, snap!!.modelId)
        assertEquals(70, snap.battery.leftPercent)
        assertEquals(100, snap.battery.rightPercent)
        assertEquals(LidState.OPEN, snap.lid)
    }

    @Test
    fun findsProximityInsideOtherContinuityTlvs() {
        val proximity = byteArrayOf(
            0x07, 0x19, 0x01,
            0x0E.toByte(), 0x20,
            0x50,
            0x55,
            0x20,
            0x01,
            0x00,
            0x00,
        )
        val wrapped = byteArrayOf(0x05, 0x03, 0x01, 0x02, 0x03) + proximity
        val snap = ProximityPairingParser.parseManufacturerData(
            data = wrapped,
            address = "11:22:33:44:55:66",
            rssi = -40,
            nowEpochMs = 2L,
        )
        assertNotNull(snap)
        assertEquals(0x0E20, snap!!.modelId)
    }

    @Test
    fun acceptsLittleEndianKnownModel() {
        val data = byteArrayOf(
            0x07, 0x19, 0x01,
            0x20, 0x14.toByte(), // LE of 0x1420 / acble 0x2014
            0x50,
            0x99.toByte(),
            0x30,
            0x01,
            0x00,
            0x00,
        )
        val snap = ProximityPairingParser.parseManufacturerData(
            data = data,
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -45,
            nowEpochMs = 3L,
        )
        assertNotNull(snap)
        assertTrue(
            snap!!.modelId == 0x2014 || snap.modelId == 0x1420,
        )
    }

    @Test
    fun rejectsNonProximity() {
        val data = byteArrayOf(0x01, 0x00)
        assertNull(ProximityPairingParser.parseManufacturerData(data, null, 0))
    }
}