package com.foldpods.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldpods.domain.AirPodsProduct
import com.foldpods.domain.BatterySnapshot
import com.foldpods.domain.ClassicHeadset
import com.foldpods.domain.ListeningMode
import com.foldpods.domain.ProCapability
import com.foldpods.domain.SnapshotSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirPodsHomeScreen(
    viewModel: FoldPodsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dismissLid by remember { mutableStateOf(false) }
    val primary: BatterySnapshot? = state.primary
    val showLid = state.showLidPopup && !dismissLid && primary != null

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(AirPodsProduct.NAME) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AirPods Pro 伴侣", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "P0 电量扫描 · P1 降噪控制 · P2 偏好 · P3 高级模式",
                style = MaterialTheme.typography.bodyMedium,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "附近 BLE 电量广播（${state.nearby.size}）· 点选关联到已连接耳机",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.classicConnected.isNotEmpty()) {
                        val labels = state.classicConnected.joinToString { h ->
                            (h.name ?: h.address) + "（系统已连接）"
                        }
                        Text(
                            "控制目标：已连上蓝牙 · $labels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (state.nearby.isEmpty()) {
                        val stats = state.scanStats
                        Text(
                            if (stats.proximityHits > 0) {
                                "已解析 Proximity ${stats.proximityHits} 次 · 附近 ${stats.nearbyCount} 台"
                            } else if (stats.applePackets > 0) {
                                "扫描中（CAPod 同款过滤）：收到 ${stats.applePackets} 条候选，" +
                                    "尚未解析出明文电量包。请开盖贴紧手机。"
                            } else {
                                "扫描中（CAPod 同款 0x07/25 过滤）。请开盖贴紧手机；" +
                                    "若 CAPod 能显示而这里一直为 0，把充电盒再开合一次。"
                            },
                        )
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            },
                        ) {
                            Text("打开系统蓝牙设置")
                        }
                    } else {
                        state.nearby.forEach { device ->
                            val address = device.address ?: return@forEach
                            val linked = address.equals(state.linkedBleAddress, ignoreCase = true) ||
                                address.equals(primary?.address, ignoreCase = true)
                            NearbyDeviceRow(
                                device = device,
                                selected = linked,
                                classicConnected = state.classicConnected.any {
                                    it.address.equals(address, ignoreCase = true)
                                },
                                onClick = { viewModel.linkBleBattery(address) },
                            )
                        }
                    }
                    state.classicConnected.forEach { h ->
                        val selected = h.address.equals(state.selectedAddress, ignoreCase = true)
                        ClassicConnectedRow(
                            headset = h,
                            selected = selected,
                            onClick = { viewModel.selectDevice(h.address) },
                        )
                    }
                }
            }

            val classicName = state.classicConnected.firstOrNull {
                it.address.equals(state.selectedAddress, ignoreCase = true)
            }?.name
            if (primary != null) {
                val cap = ProCapability.forModelId(primary.modelId)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "电量 · ${classicName ?: primary.modelLabel}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                primary.source == SnapshotSource.AACP ->
                                    "电量来源：AACP（与 CAPod 同路径）· ${primary.address}"
                                primary.address.equals(state.selectedAddress, true) ->
                                    "地址一致：${primary.address}"
                                state.linkedBleAddress != null &&
                                    primary.address.equals(state.linkedBleAddress, true) ->
                                    "BLE 电量已手动关联：${primary.address}（经典 ${state.selectedAddress}）"
                                state.batteryLinkedHeuristically ->
                                    "BLE 电量启发式关联：${primary.address}（经典 ${state.selectedAddress}）。" +
                                        "若不对请在上方点选正确广播。"
                                else ->
                                    "BLE：${primary.address} · 经典：${state.selectedAddress ?: "—"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("型号：${primary.modelLabel} · 盒盖：${primary.lid} · RSSI：${primary.rssi}")
                        BatteryRow("左耳", primary.battery.leftPercent, primary.battery.leftCharging)
                        BatteryRow("右耳", primary.battery.rightPercent, primary.battery.rightCharging)
                        BatteryRow("充电盒", primary.battery.casePercent, primary.battery.caseCharging)
                        Text(
                            "能力：Adaptive=${cap.supportsAdaptive} · 对话感知=${cap.supportsConversationalAwareness}",
                        )
                    }
                }
            } else if (state.selectedAddress != null) {
                val classic = state.classicConnected.firstOrNull {
                    it.address.equals(state.selectedAddress, ignoreCase = true)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "已选中 · ${classicName ?: classic?.name ?: "系统已连接耳机"}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("经典地址：${state.selectedAddress}")
                        val systemPct = classic?.systemBatteryPercent
                        if (systemPct != null) {
                            Text("系统电量（整机）：$systemPct%")
                            LinearProgressIndicator(
                                progress = { systemPct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "左右耳 / 充电盒分立电量需开盖后的 BLE 邻近广播；点选上方广播可关联。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                "暂无可用的 BLE 电量广播，系统也未报告整机电量。请开盖靠近；" +
                                    "出现列表后点选一条即可关联。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("P1 · AACP 控制", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AACP=${if (state.connection.aacpConnected) "已连接" else "未连接"} · " +
                            "L2CAP=${when {
                                state.connection.l2capAvailable -> "可用"
                                state.connection.l2capNeedsRootHint -> "创建失败"
                                else -> "未探测"
                            }}",
                    )
                    Text(
                        "说明：电量优先走 AACP（L2CAP PSM 0x1001，与 CAPod 同路径）；" +
                            "A2DP 已连会自动尝试建连。BLE 明文邻近包作补充。" +
                            "降噪指令也依赖同一条 AACP；失败时仍可用机身长按切换。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.statusMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::probeL2cap) { Text("探测 L2CAP") }
                        Button(onClick = viewModel::connectAacp) { Text("连接 AACP") }
                        OutlinedButton(onClick = viewModel::assistReconnect) { Text("辅助重连") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListeningMode.entries.forEach { mode ->
                            val hideAdaptive = mode == ListeningMode.ADAPTIVE &&
                                primary?.let { !ProCapability.forModelId(it.modelId).supportsAdaptive } == true
                            if (hideAdaptive) return@forEach
                            FilterChip(
                                selected = state.listeningMode == mode,
                                onClick = { viewModel.setMode(mode) },
                                enabled = state.connection.aacpConnected,
                                label = { Text(modeLabel(mode)) },
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::cycleMode,
                        enabled = state.connection.aacpConnected,
                    ) {
                        Text("循环降噪模式（同 QS Tile）")
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("P2 · 体验偏好", style = MaterialTheme.typography.titleMedium)
                    PrefSwitch("摘下暂停媒体", state.prefs.pauseOnRemove) {
                        viewModel.updatePrefs { p -> p.copy(pauseOnRemove = it) }
                    }
                    PrefSwitch("对话感知", state.prefs.conversationalAwareness) {
                        viewModel.updatePrefs { p -> p.copy(conversationalAwareness = it) }
                    }
                    PrefSwitch("点头接听（实验）", state.prefs.headGestures) {
                        viewModel.updatePrefs { p -> p.copy(headGestures = it) }
                    }
                    Text("入耳：L=${state.ear.left} R=${state.ear.right}")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("P3 · 高级模式", style = MaterialTheme.typography.titleMedium)
                    PrefSwitch(
                        "启用高级模式（通透细调 / 助听 / 多点需 root）",
                        state.prefs.advancedModeEnabled,
                    ) {
                        viewModel.updatePrefs { p -> p.copy(advancedModeEnabled = it) }
                    }
                    if (state.prefs.advancedModeEnabled) {
                        Text(
                            "侧载极客功能：VendorID 伪装与系统级电量需 Xposed/root。" +
                                "默认路径不启用。详见 docs/foldpods-capabilities.md。",
                        )
                        Text("· 通透细调 / Conversation Boost：需伪装 Apple VendorID")
                        Text("· Hearing Aid：导入听力图（不做机内测听）")
                        Text("· Bluetooth Multipoint：双设备连接")
                    }
                }
            }
        }
    }

    if (showLid && primary != null) {
        AlertDialog(
            onDismissRequest = { dismissLid = true },
            title = { Text("AirPods 已开盖") },
            text = {
                Column {
                    Text(primary.modelLabel)
                    Text(
                        "左 ${fmt(primary.battery.leftPercent)} · " +
                            "右 ${fmt(primary.battery.rightPercent)} · " +
                            "盒 ${fmt(primary.battery.casePercent)}",
                    )
                    Text("已尝试辅助重连已配对设备（不保证成功）。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissLid = true
                        viewModel.assistReconnect()
                    },
                ) { Text("再连一次") }
            },
            dismissButton = {
                TextButton(onClick = { dismissLid = true }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun NearbyDeviceRow(
    device: BatterySnapshot,
    selected: Boolean,
    classicConnected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            val suffix = buildString {
                if (classicConnected) append(" · 已连接")
                if (selected) append("（电量关联）")
            }
            Text(
                device.modelLabel + suffix,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${device.address} · 盒盖 ${device.lid} · " +
                    "L ${fmt(device.battery.leftPercent)} R ${fmt(device.battery.rightPercent)} " +
                    "盒 ${fmt(device.battery.casePercent)} · ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ClassicConnectedRow(
    headset: ClassicHeadset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (headset.name ?: "已连接耳机") +
                    " · 系统蓝牙" +
                    if (selected) "（控制目标）" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${headset.address} · A2DP=${headset.a2dpConnected} · HFP=${headset.hfpConnected}" +
                    (headset.systemBatteryPercent?.let { " · 电量 $it%" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BatteryRow(label: String, percent: Int?, charging: Boolean) {
    Column {
        Text("$label ${fmt(percent)}${if (charging) " · 充电中" else ""}")
        LinearProgressIndicator(
            progress = { (percent ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrefSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun fmt(p: Int?): String = p?.let { "$it%" } ?: "—"

private fun modeLabel(mode: ListeningMode): String = when (mode) {
    ListeningMode.OFF -> "关"
    ListeningMode.TRANSPARENCY -> "通透"
    ListeningMode.NOISE_CANCELLATION -> "降噪"
    ListeningMode.ADAPTIVE -> "自适应"
}
