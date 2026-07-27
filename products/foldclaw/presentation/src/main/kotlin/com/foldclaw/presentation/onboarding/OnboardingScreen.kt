package com.foldclaw.presentation.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.foldclaw.device.a11y.FoldClawAccessibilityService
import com.foldclaw.presentation.theme.FoldClawColors

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    var a11yOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(FoldClawColors.Mist, FoldClawColors.Foam, FoldClawColors.MistDeep),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "FoldClaw",
            style = MaterialTheme.typography.displaySmall,
            color = FoldClawColors.Ink,
        )
        Text(
            "个人侧载 AI 助手。闹钟/日历可走系统 Intent；读界面、点击、输入等通用 Claw 能力必须开启无障碍。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (a11yOn) "✓ 无障碍服务已开启" else "○ 无障碍服务未开启（通用自动化不可用）",
            style = MaterialTheme.typography.bodyLarge,
            color = if (a11yOn) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            "应用不能自动打开系统授权页。若开启了 Auto Blocker / 受限设置，请先在系统设置中允许 FoldClaw。未开无障碍时，仅 Intent 类任务（闹钟/日历/打开应用）可能可用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (!a11yOn) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("去开启无障碍")
            }
        }
        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (a11yOn) "进入助手" else "稍后开启，先试用闹钟/日历")
        }
    }
}

fun isAccessibilityEnabled(context: Context): Boolean {
    if (FoldClawAccessibilityService.instance != null) return true
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val expected = "${context.packageName}/com.foldclaw.device.a11y.FoldClawAccessibilityService"
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) } ||
        enabled.contains("FoldClawAccessibilityService")
}
