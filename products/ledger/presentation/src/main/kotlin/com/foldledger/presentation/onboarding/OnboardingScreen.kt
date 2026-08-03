package com.foldledger.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foldledger.capture.a11y.LedgerAccessibilityService
import com.foldledger.capture.fgs.CaptureKeepAliveService
import com.foldledger.capture.nls.LedgerNotificationListener
import com.foldledger.domain.repo.SettingsRepository
import com.foldledger.presentation.common.LedgerCard
import com.foldledger.presentation.common.FoldLedgerMark
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settings: SettingsRepository,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The system result is reflected on the settings screen. */ }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FoldLedgerMark(modifier = Modifier.size(64.dp))
        Text(
            "让每一笔消费\n自然归位",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            "FoldLedger 在本机识别支付通知和成功页面，不会替你点击、付款，也不会上传账本。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LedgerCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text("开始前，需要完成这些设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "点击每一项会打开对应的系统页面，完成后返回即可。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        PermissionStep(
            number = "01",
            icon = Icons.Default.NotificationsActive,
            title = "通知与通知使用权",
            description = "允许保活通知，并读取微信、支付宝的支付结果。",
            primaryLabel = "允许通知",
            onPrimary = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            secondaryLabel = "打开通知使用权",
            onSecondary = { LedgerNotificationListener.openSettings(context) },
        )
        PermissionStep(
            number = "02",
            icon = Icons.Default.Visibility,
            title = "支付页面识别",
            description = "只读识别支付宝成功页，用来补全可能缺失的通知。",
            primaryLabel = "打开无障碍设置",
            onPrimary = { LedgerAccessibilityService.openSettings(context) },
        )
        PermissionStep(
            number = "03",
            icon = Icons.Default.Layers,
            title = "悬浮确认",
            description = "捕获到账单后，可在当前应用上方快速确认。",
            primaryLabel = "允许悬浮窗",
            onPrimary = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )
        PermissionStep(
            number = "04",
            icon = Icons.Default.BatteryChargingFull,
            title = "后台稳定运行",
            description = "忽略电池优化，并建议在 One UI 最近任务中锁定本应用。",
            primaryLabel = "忽略电池优化",
            onPrimary = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            },
        )

        Button(
            onClick = {
                CaptureKeepAliveService.start(context)
                scope.launch {
                    settings.setOnboardingDone(true)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("完成设置，进入账本")
        }
        TextButton(
            onClick = {
                scope.launch {
                    settings.setOnboardingDone(true)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("暂时跳过")
        }
    }
}

@Composable
private fun PermissionStep(
    number: String,
    icon: ImageVector,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    LedgerCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrimary) { Text(primaryLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}
