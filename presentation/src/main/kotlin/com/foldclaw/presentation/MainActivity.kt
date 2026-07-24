package com.foldclaw.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.foldclaw.presentation.chat.ChatScreen
import com.foldclaw.presentation.onboarding.OnboardingScreen
import com.foldclaw.presentation.onboarding.isAccessibilityEnabled
import com.foldclaw.presentation.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

private enum class AppScreen { Chat, Settings }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
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
                        )
                    }
                }
            }
        }
    }
}
