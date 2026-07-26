package com.foldclaw.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 按一次开始录音，再按一次结束并走百炼 ASR（不依赖 Google 语音识别）。
 */
@Composable
fun rememberVoiceToggleHandler(
    isRecording: Boolean,
    isTranscribing: Boolean,
    onToggle: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onToggle()
        } else {
            Toast.makeText(context, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
        }
        pending = false
    }

    return remember(isRecording, isTranscribing, onToggle) {
        {
            if (isTranscribing) {
                Toast.makeText(context, "正在识别…", Toast.LENGTH_SHORT).show()
                return@remember
            }
            if (isRecording) {
                onToggle()
                return@remember
            }
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onToggle()
            } else if (!pending) {
                pending = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/**
 * 助理/磁贴唤起：确保麦克风权限后开始录音。
 */
@Composable
fun AutoStartVoiceEffect(
    enabled: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isRunning: Boolean,
    onStart: () -> Unit,
    onConsumed: () -> Unit,
) {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onStart()
        } else {
            Toast.makeText(context, "需要麦克风权限才能语音唤起", Toast.LENGTH_SHORT).show()
        }
        onConsumed()
        asked = false
    }

    LaunchedEffect(enabled, isRecording, isTranscribing, isRunning) {
        if (!enabled) return@LaunchedEffect
        if (isRecording || isTranscribing || isRunning) {
            onConsumed()
            return@LaunchedEffect
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onStart()
            onConsumed()
        } else if (!asked) {
            asked = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
