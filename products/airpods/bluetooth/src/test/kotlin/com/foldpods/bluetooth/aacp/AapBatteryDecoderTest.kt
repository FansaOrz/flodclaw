package com.foldpods.bluetooth.aacp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AapBatteryDecoderTest {

    @Test
    fun decodesLeftRightCase() {
        // Message packet type 0x0004, cmd 0x0004, 3 entries
        val frame = byteArrayOf(
            0x04, 0x00, // packet type Message
            0x00, 0x00, // service (ignored for decode)
            0x04, 0x00, // BATTERY_INFO
            0x03, // count
            // LEFT 88% charging
            0x04, 0x00, 0x58, 0x01, 0x00,
            // RIGHT 76% not charging
            0x02, 0x00, 0x4C, 0x02, 0x00,
            // CASE 40%
            0x08, 0x00, 0x28, 0x02, 0x00,
        )
        val levels = AapBatteryDecoder.tryDecodeBattery(frame)
        assertNotNull(levels)
        assertEquals(88, levels!!.leftPercent)
        assertEquals(76, levels.rightPercent)
        assertEquals(40, levels.casePercent)
        assertEquals(true, levels.leftCharging)
        assertEquals(false, levels.rightCharging)
    }

    @Test
    fun ignoresNonBatteryFrames() {
        val handshakeResp = byteArrayOf(0x01, 0x00, 0x04, 0x00, 0x00, 0x00)
        assertNull(AapBatteryDecoder.tryDecodeBattery(handshakeResp))
    }
}
