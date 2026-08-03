package com.foldledger.presentation.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldledger.capture.a11y.LedgerAccessibilityService
import com.foldledger.capture.fgs.CaptureKeepAliveService
import com.foldledger.domain.model.DuplicatePair
import com.foldledger.domain.repo.TransactionRepository
import com.foldledger.presentation.common.DuplicateConfirmDialog
import com.foldledger.presentation.common.LedgerCard
import com.foldledger.presentation.common.LedgerPageHeader
import com.foldledger.capture.importing.BillImportUseCase
import com.foldledger.capture.nls.LedgerNotificationListener
import com.foldledger.capture.overlay.ConfirmOverlayController
import com.foldledger.capture.sms.BankSmsScanner
import com.foldledger.data.parse.BillFileLoader
import com.foldledger.domain.repo.BackupRepository
import com.foldledger.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUi(
    val autoConfirm: Boolean = false,
    val clearRaw: Boolean = false,
    val nlsGranted: Boolean = false,
    val nlsConnected: Boolean = false,
    val a11yEnabled: Boolean = false,
    val overlay: Boolean = false,
    val batteryIgnored: Boolean = false,
    val smsGranted: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val overlay: ConfirmOverlayController,
    private val smsScanner: BankSmsScanner,
    private val billImport: BillImportUseCase,
    private val billFileLoader: BillFileLoader,
    private val transactions: TransactionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val ui: StateFlow<SettingsUi> = combine(
        settings.autoConfirm,
        settings.clearRawAfterSave,
    ) { auto, clear ->
        SettingsUi(
            autoConfirm = auto,
            clearRaw = clear,
            nlsGranted = LedgerNotificationListener.isAccessGranted(context),
            nlsConnected = LedgerNotificationListener.connected,
            a11yEnabled = LedgerAccessibilityService.isEnabled(context),
            overlay = overlay.canDrawOverlays(),
            batteryIgnored = isIgnoringBattery(context),
            smsGranted = smsScanner.hasPermission(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setAutoConfirm(v: Boolean) = viewModelScope.launch { settings.setAutoConfirm(v) }
    fun setClearRaw(v: Boolean) = viewModelScope.launch { settings.setClearRawAfterSave(v) }

    suspend fun exportCsv(): String = backup.exportCsv()
    suspend fun exportJson(): String = backup.exportJsonBackup()
    suspend fun exportCategories(): String = backup.exportCategoriesJson()
    suspend fun importBackupUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext "无法打开所选备份文件。"
        backup.importJsonBackup(bytes.toString(Charsets.UTF_8))
        "完整备份恢复完成。账户、分类和预算已更新；已有流水按指纹去重，新流水已导入。"
    }

    suspend fun importCategoriesUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext "无法打开所选文件。"
        backup.importCategoriesJson(bytes.toString(Charsets.UTF_8))
    }

    suspend fun scanSms(days: Int = 90): String = withContext(Dispatchers.IO) {
        val r = smsScanner.scanInbox(days)
        "已扫描 ${r.scanned} 条短信，匹配银行类 ${r.matched} 条，已提交待确认 ${r.submitted} 条。请回流水页查看。"
    }

    suspend fun importBillUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri)
        val mime = context.contentResolver.getType(uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext "无法打开所选文件（权限或文件已失效），请重新选择。"
        when (val loaded = billFileLoader.load(bytes, name, mime)) {
            is BillFileLoader.LoadResult.Err -> loaded.message
            is BillFileLoader.LoadResult.Ok -> {
                val auto = settings.autoConfirm.first()
                val r = billImport.importCsv(loaded.csvText, forceAutoAll = auto)
                val prefix = if (loaded.hint.isNotBlank()) "${loaded.hint}\n\n" else ""
                val nameLine = if (!name.isNullOrBlank()) "文件：$name\n\n" else ""
                nameLine + prefix + r.userMessage()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        }.getOrNull()
    }

    suspend fun scanDuplicates(): List<DuplicatePair> = withContext(Dispatchers.IO) {
        transactions.findDuplicatePairs()
    }

    fun softDeleteTransaction(id: Long) {
        viewModelScope.launch { transactions.softDelete(id) }
    }

    companion object {
        fun isIgnoringBattery(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importProgressText by remember { mutableStateOf("正在读取并解析账单，请稍候…") }
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    var scanningDup by remember { mutableStateOf(false) }
    var duplicateQueue by remember { mutableStateOf<List<DuplicatePair>>(emptyList()) }

    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        dialogMessage = if (result.values.any { it }) {
            "短信权限已授予，可扫描收件箱。"
        } else {
            "未授予短信权限。"
        }
    }

    val categoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            dialogMessage = "已取消选择文件。"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            dialogMessage = try {
                viewModel.importCategoriesUri(uri)
            } catch (t: Throwable) {
                "导入分类失败：${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            dialogMessage = "已取消选择文件。"
        } else {
            pendingBackupUri = uri
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            dialogMessage = "已取消选择文件。"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importing = true
            importProgressText = "正在读取并解析账单，请稍候…"
            dialogMessage = try {
                viewModel.importBillUri(uri)
            } catch (t: Throwable) {
                "导入失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                importing = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LedgerPageHeader(
                title = "设置",
                eyebrow = "权限、导入与数据",
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(
                title = "权限与监听",
                caption = "通知、无障碍、悬浮窗与保活，保证实时抓取可用。",
            ) {
                StatusLine("通知使用权", state.nlsGranted)
                StatusLine("通知监听已连接", state.nlsConnected)
                StatusLine("无障碍（微信/支付宝页面）", state.a11yEnabled)
                StatusLine("悬浮窗", state.overlay)
                StatusLine("短信权限", state.smsGranted)
                StatusLine("忽略电池优化", state.batteryIgnored)
                TextButton(onClick = { LedgerNotificationListener.openSettings(context) }) {
                    Text("打开通知使用权设置")
                }
                TextButton(onClick = { LedgerAccessibilityService.openSettings(context) }) {
                    Text("打开无障碍设置（改完请先关再开）")
                }
                TextButton(
                    onClick = {
                        val snap = LedgerAccessibilityService.instance?.debugSnapshot()
                            ?: "无障碍服务未连接。请到系统设置关掉再打开 FoldLedger 无障碍。"
                        dialogMessage = snap
                    },
                ) { Text("诊断：读取当前前台页面文本") }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                ) { Text("打开悬浮窗权限") }
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            },
                        )
                    },
                ) { Text("忽略电池优化（One UI 保活）") }
                OutlinedButton(onClick = { CaptureKeepAliveService.start(context) }) {
                    Text("启动保活前台服务")
                }
            }

            SettingsSection(
                title = "账单导入",
                caption = "支持 CSV / Excel。微信导入后自动入库；支付宝等默认进待确认。",
            ) {
                Text(
                    "微信：我 → 服务 → 钱包 → 账单 → 右上角 → 导出账单\n支付宝：我的 → 账单 → … → 开具交易流水/导出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Button(
                    onClick = {
                        csvPicker.launch(
                            arrayOf(
                                "text/*",
                                "text/csv",
                                "application/csv",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/zip",
                                "application/x-zip-compressed",
                                "*/*",
                            ),
                        )
                    },
                    enabled = !importing && !scanningDup,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (importing) "正在导入…" else "导入账单") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            scanningDup = true
                            try {
                                val pairs = viewModel.scanDuplicates()
                                if (pairs.isEmpty()) {
                                    dialogMessage = "未发现疑似重复账单。"
                                } else {
                                    duplicateQueue = pairs
                                }
                            } catch (t: Throwable) {
                                dialogMessage = "筛查失败：${t.message ?: t.javaClass.simpleName}"
                            } finally {
                                scanningDup = false
                            }
                        }
                    },
                    enabled = !importing && !scanningDup,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (scanningDup) "正在筛查…" else "全局重复筛查") }
                Text(
                    "查找同金额、时间接近、商户相似的叠记账（如实时抓取与账单导入重复），可逐笔确认保留哪一笔。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }

            SettingsSection(
                title = "银行短信",
                caption = "可读收件箱历史短信，并在收到新短信时自动解析。",
            ) {
                TextButton(
                    onClick = {
                        val need = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                        val missing = need.any {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing) smsPermission.launch(need)
                        else dialogMessage = "短信权限已具备"
                    },
                ) { Text("申请短信权限") }
                TextButton(
                    onClick = {
                        scope.launch {
                            dialogMessage = if (!viewModel.ui.value.smsGranted &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                                != PackageManager.PERMISSION_GRANTED
                            ) {
                                "请先授予短信权限"
                            } else {
                                viewModel.scanSms(90)
                            }
                        }
                    },
                ) { Text("扫描近 90 天短信并记账") }
            }

            SettingsSection(
                title = "记账行为",
                caption = "控制确认流程与原始文案保留。",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("全自动入库（跳过确认）")
                        Text(
                            "实时抓取与支付宝导入也会直接入库；微信导入始终自动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Switch(checked = state.autoConfirm, onCheckedChange = viewModel::setAutoConfirm)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("入库后清空原始文案")
                    Switch(checked = state.clearRaw, onCheckedChange = viewModel::setClearRaw)
                }
            }

            SettingsSection(
                title = "备份与分类",
                caption = "流水用完整 JSON；分类关键词可单独导出，重装后加载即可恢复标签规则。",
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            shareText(context, "foldledger.csv", viewModel.exportCsv(), "text/csv")
                            dialogMessage = "已导出 CSV"
                        }
                    },
                ) { Text("导出流水 CSV") }
                TextButton(
                    onClick = {
                        scope.launch {
                            shareText(context, "foldledger-backup.json", viewModel.exportJson(), "application/json")
                            dialogMessage = "已导出完整 JSON（含账户/分类/流水）"
                        }
                    },
                ) { Text("导出完整 JSON 备份") }
                Button(
                    onClick = {
                        backupPicker.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("恢复完整 JSON 备份") }
                TextButton(
                    onClick = {
                        scope.launch {
                            shareText(
                                context,
                                "foldledger-categories.json",
                                viewModel.exportCategories(),
                                "application/json",
                            )
                            dialogMessage = "已导出分类与关键词。重装后用「加载分类」选这个文件即可。"
                        }
                    },
                ) { Text("导出分类与关键词") }
                TextButton(
                    onClick = {
                        categoryPicker.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                ) { Text("加载分类与关键词") }
            }

            Text(
                "实时抓取依赖：通知使用权 + 无障碍 + 悬浮窗。微信/支付宝常不发系统通知，无障碍更关键。银行短信往往只有金额、没有商户。历史账单请用官方导出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    pendingBackupUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingBackupUri = null },
            title = { Text("恢复完整备份？") },
            text = {
                Text(
                    "账户、分类和预算将按备份内容更新；流水会按指纹去重后合并，" +
                        "不会先清空当前数据。建议仅导入你信任的 FoldLedger JSON 备份。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingBackupUri = null
                        scope.launch {
                            importing = true
                            importProgressText = "正在校验并恢复完整备份，请稍候…"
                            dialogMessage = try {
                                viewModel.importBackupUri(uri)
                            } catch (t: Throwable) {
                                "恢复失败：${t.message ?: t.javaClass.simpleName}"
                            } finally {
                                importing = false
                            }
                        }
                    },
                ) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackupUri = null }) { Text("取消") }
            },
        )
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在导入") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(importProgressText)
                }
            },
            confirmButton = {},
        )
    }

    if (duplicateQueue.isNotEmpty()) {
        DuplicateConfirmDialog(
            pairs = duplicateQueue,
            onKeepFirst = { pair ->
                viewModel.softDeleteTransaction(pair.second.transaction.id)
            },
            onKeepSecond = { pair ->
                viewModel.softDeleteTransaction(pair.first.transaction.id)
            },
            onKeepBoth = { },
            onDismissAll = { duplicateQueue = emptyList() },
        )
    }

    dialogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { dialogMessage = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { dialogMessage = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    caption: String,
    content: @Composable () -> Unit,
) {
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (ok) "正常" else "未就绪",
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun shareText(context: Context, name: String, content: String, mime: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, name).apply { writeText(content) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "导出"))
}
