package com.foldclaw.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.foldclaw.presentation.assist.AssistLaunch
import com.foldclaw.presentation.chat.ChatScreen
import com.foldclaw.presentation.onboarding.OnboardingScreen
import com.foldclaw.presentation.onboarding.isAccessibilityEnabled
import com.foldclaw.presentation.settings.SettingsScreen
import com.foldclaw.presentation.theme.FoldClawTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class AppScreen { Chat, Settings }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingAutoVoice by mutableStateOf(false)
    private var pendingAutoSend by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ingestLaunchIntent(intent)
        enableEdgeToEdge()
        setContent {
            FoldClawTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val prefs = remember {
                        context.getSharedPreferences("foldclaw_prefs", MODE_PRIVATE)
                    }
                    var showOnboarding by remember {
                        mutableStateOf(
                            !prefs.getBoolean("onboarding_done", false) &&
                                !isAccessibilityEnabled(context),
                        )
                    }
                    var screen by remember { mutableStateOf(AppScreen.Chat) }

                    // 助理/磁贴唤起时跳过引导，直接进聊天开麦
                    LaunchedEffect(pendingAutoVoice) {
                        if (pendingAutoVoice) {
                            showOnboarding = false
                            screen = AppScreen.Chat
                        }
                    }

                    when {
                        showOnboarding -> OnboardingScreen(
                            onContinue = {
                                prefs.edit().putBoolean("onboarding_done", true).apply()
                                showOnboarding = false
                            },
                        )
                        screen == AppScreen.Settings -> SettingsScreen(
                            onBack = { screen = AppScreen.Chat },
                        )
                        else -> ChatScreen(
                            onOpenSettings = { screen = AppScreen.Settings },
                            autoStartVoice = pendingAutoVoice,
                            autoSendAfterVoice = pendingAutoSend,
                            onAutoStartConsumed = {
                                pendingAutoVoice = false
                                pendingAutoSend = false
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ingestLaunchIntent(intent)
    }

    private fun ingestLaunchIntent(intent: Intent?) {
        if (AssistLaunch.wantsAutoVoice(intent)) {
            pendingAutoVoice = true
            pendingAutoSend = AssistLaunch.wantsAutoSend(intent)
        }
    }
}
