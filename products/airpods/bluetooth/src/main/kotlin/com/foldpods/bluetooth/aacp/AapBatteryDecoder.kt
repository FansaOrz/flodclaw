package com.foldpods.bluetooth.aacp

/**
 * 最小 AAP 帧解析 + 电量解码（对齐 CAPod DefaultAapDeviceProfile.decodeBattery）。
 * L2CAP SEQPACKET 通常一次 read 一帧。
 */
internal object AapBatteryDecoder {

    const val CMD_BATTERY_INFO = 0x0004

    data class Levels(
        val leftPercent: Int? = null,
        val rightPercent: Int? = null,
        val casePercent: Int? = null,
        val singlePercent: Int? = null,
        val leftCharging: Boolean = false,
        val rightCharging: Boolean = false,
        val caseCharging: Boolean = false,
        val singleCharging: Boolean = false,
    )

    fun tryDecodeBattery(frame: ByteArray): Levels? {
        if (frame.size < 6) return null
        val packetType = readLe16(frame, 0)
        if (packetType != 0x0004) return null // Message
        val command = readLe16(frame, 4)
        if (command != CMD_BATTERY_INFO) return null

        val payload = if (frame.size > 6) frame.copyOfRange(6, frame.size) else return null
        if (payload.isEmpty()) return null

        val count = payload[0].toInt() and 0xFF
        if (count == 0) return Levels()

        var left: Int? = null
        var right: Int? = null
        var casePct: Int? = null
        var single: Int? = null
        var leftCh = false
        var rightCh = false
        var caseCh = false
        var singleCh = false

        var offset = 1
        repeat(count) {
            if (offset + 5 > payload.size) return@repeat
            val type = payload[offset].toInt() and 0xFF
            val percent = payload[offset + 2].toInt() and 0xFF
            val chargingWire = payload[offset + 3].toInt() and 0xFF
            offset += 5

            if (percent > 100) return@repeat
            val charging = chargingWire == 0x01 || chargingWire == 0x05

            when (type) {
                0x01 -> {
                    single = percent
                    singleCh = charging
                }
                0x02 -> {
                    right = percent
                    rightCh = charging
                }
                0x04 -> {
                    left = percent
                    leftCh = charging
                }
                0x08 -> {
                    casePct = percent
                    caseCh = charging
                }
            }
        }

        return Levels(
            leftPercent = left,
            rightPercent = right,
            casePercent = casePct,
            singlePercent = single,
            leftCharging = leftCh,
            rightCharging = rightCh,
            caseCharging = caseCh,
            singleCharging = singleCh,
        )
    }

    private fun readLe16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}
