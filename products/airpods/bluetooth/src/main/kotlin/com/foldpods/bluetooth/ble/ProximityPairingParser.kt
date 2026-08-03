package com.foldpods.bluetooth.ble

import com.foldpods.domain.BatteryLevels
import com.foldpods.domain.BatterySnapshot
import com.foldpods.domain.LidState
import com.foldpods.domain.ProCapability
import com.foldpods.domain.SnapshotSource

/**
 * Apple Continuity Proximity Pairing (type 0x07) parser.
 *
 * 对抗性要点：
 * 1. 厂商数据里可能有多段 Continuity TLV，0x07 不一定在 offset 0
 * 2. 部分 OEM 会截断广播，declared length 不能当作硬门槛
 * 3. 型号字可能是 BE（社区主流）或 LE；优先匹配已知型号
 */
object ProximityPairingParser {
    const val APPLE_COMPANY_ID = 0x004C
    const val TYPE_PROXIMITY = 0x07

    /** Android Log / 单测 no-op 注入点，避免 JVM 单测依赖 android.util.Log。 */
    @Volatile
    var diagnostic: ((level: String, message: String) -> Unit)? = null

    fun parseManufacturerData(
        data: ByteArray,
        address: String?,
        rssi: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
        requireKnownModel: Boolean = false,
    ): BatterySnapshot? {
        val proximity = findProximityPayload(data) ?: run {
            logReject(address, data, "no_0x07_tlv")
            return null
        }
        return parseProximityMessage(
            msg = proximity,
            address = address,
            rssi = rssi,
            nowEpochMs = nowEpochMs,
            requireKnownModel = requireKnownModel,
        )
    }

    fun findProximityPayload(data: ByteArray): ByteArray? {
        // 只认 TLV 对齐的 type=0x07，禁止在载荷里“撞到”0x07（会把 Nearby/其它 Continuity 误判成耳机）
        var i = 0
        while (i + 10 < data.size) {
            val type = data[i].toInt() and 0xFF
            val len = data[i + 1].toInt() and 0xFF
            if (type == TYPE_PROXIMITY && (data.size - i) >= 11) {
                // OEM 常截断广播：declared length 可以大于实际剩余字节
                return data.copyOfRange(i, data.size)
            }
            if (len <= 0) {
                i++
                continue
            }
            val end = i + 2 + len
            if (end > data.size) break
            i = end
        }
        return null
    }

    private fun parseProximityMessage(
        msg: ByteArray,
        address: String?,
        rssi: Int,
        nowEpochMs: Long,
        requireKnownModel: Boolean,
    ): BatterySnapshot? {
        if (msg.size < 11) {
            logReject(address, msg, "too_short=${msg.size}")
            return null
        }
        if ((msg[0].toInt() and 0xFF) != TYPE_PROXIMITY) return null

        val declaredLen = msg[1].toInt() and 0xFF
        // CAPod: 明文电量包的 payload 首字节必须是 0x01（paired status broadcast）
        val pairingMode = msg[2].toInt() and 0xFF
        if (pairingMode != 0x01 && pairingMode != 0x00) {
            logReject(address, msg, "bad_pairing=$pairingMode")
            return null
        }
        // 优先接受 0x01（与 CAPod PAIRING_MESSAGE_PREFIX 一致）；0x00 仅 pairing 模式偶发
        if (pairingMode == 0x00) {
            diagnostic?.invoke("D", "pairing-mode packet addr=$address (rare)")
        }

        val modelBe = ((msg[3].toInt() and 0xFF) shl 8) or (msg[4].toInt() and 0xFF)
        val modelLe = ((msg[4].toInt() and 0xFF) shl 8) or (msg[3].toInt() and 0xFF)
        val modelId = when {
            ProCapability.isKnownModel(modelBe) -> modelBe
            ProCapability.isKnownModel(modelLe) -> modelLe
            else -> modelBe
        }
        if (requireKnownModel && !ProCapability.isKnownModel(modelId)) {
            logReject(address, msg, "unknown_model=0x%04X".format(modelId))
            return null
        }

        val podsBattery = msg[6].toInt() and 0xFF
        val flagsCase = msg[7].toInt() and 0xFF
        val lidByte = msg[8].toInt() and 0xFF
        val color = msg[9].toInt() and 0xFF

        val left = nibbleToPercent(podsBattery and 0x0F)
        val right = nibbleToPercent((podsBattery shr 4) and 0x0F)
        val casePct = nibbleToPercent((flagsCase shr 4) and 0x0F)
        val chargeFlags = flagsCase and 0x0F
        if (left == null && right == null && casePct == null) {
            logReject(address, msg, "no_battery_nibbles")
            return null
        }

        if (!ProCapability.isKnownModel(modelId)) {
            val lenOk = declaredLen in 0x0A..0x20 || msg.size >= 11
            if (!(color in 0x00..0x0F && lenOk)) {
                logReject(address, msg, "implausible_unknown=0x%04X".format(modelId))
                return null
            }
        }

        val lid = when {
            (lidByte and 0x01) != 0 -> LidState.OPEN
            (lidByte and 0x02) != 0 -> LidState.CLOSED
            else -> if ((lidByte and 0x0F) != 0) LidState.OPEN else LidState.UNKNOWN
        }

        val capability = ProCapability.forModelId(modelId)
        diagnostic?.invoke(
            "I",
            "proximity ok addr=$address model=0x%04X known=${ProCapability.isKnownModel(modelId)} " +
                "rssi=$rssi lid=$lid L=$left R=$right C=$casePct hex=${msg.toHex(16)}",
        )
        return BatterySnapshot(
            address = address,
            modelId = modelId,
            modelLabel = capability.label,
            battery = BatteryLevels(
                leftPercent = left,
                rightPercent = right,
                casePercent = casePct,
                leftCharging = chargeFlags and 0x01 != 0,
                rightCharging = chargeFlags and 0x02 != 0,
                caseCharging = chargeFlags and 0x04 != 0,
            ),
            lid = lid,
            colorCode = color,
            rssi = rssi,
            updatedAtEpochMs = nowEpochMs,
            source = SnapshotSource.BLE_PROXIMITY,
        )
    }

    private fun nibbleToPercent(nibble: Int): Int? {
        if (nibble in 0..9) return nibble * 10
        if (nibble == 10) return 100
        return null
    }

    private fun logReject(address: String?, data: ByteArray, reason: String) {
        diagnostic?.invoke("D", "reject addr=$address reason=$reason hex=${data.toHex(24)}")
    }

    private fun ByteArray.toHex(max: Int = size): String =
        take(max).joinToString("") { "%02X".format(it) }
}
