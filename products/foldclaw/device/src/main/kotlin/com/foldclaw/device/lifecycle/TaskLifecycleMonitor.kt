package com.foldclaw.device.lifecycle

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.foldclaw.agent.AgentOrchestrator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听锁屏：任务运行中若锁屏则取消 run（恢复为 PAUSED_LOCKED 语义由 Orchestrator 循环检测；
 * 此处主动 cancel 避免在锁屏后继续副作用）。
 * 不因切到目标 App（本 App ON_STOP）而取消——那是预期执行模式。
 */
@Singleton
class TaskLifecycleMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orchestrator: AgentOrchestrator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _locked = MutableStateFlow(keyguard.isKeyguardLocked)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val nowLocked = keyguard.isKeyguardLocked || !power.isInteractive
            _locked.value = nowLocked
            if (nowLocked) {
                scope.launch { orchestrator.cancel() }
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(receiver, filter)
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}
