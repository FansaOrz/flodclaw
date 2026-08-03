package com.foldpods.presentation.media

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.foldpods.domain.EarDetectionState
import com.foldpods.domain.EarPresence
import com.foldpods.domain.FoldPodsPrefsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2: when both ears out and pauseOnRemove enabled, send media pause.
 * Ear presence is updated when AACP ear packets are wired; until then this is inert.
 */
@Singleton
class EarPauseController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsStore: FoldPodsPrefsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastBothOut = false

    fun start(earFlow: kotlinx.coroutines.flow.Flow<EarDetectionState>) {
        job?.cancel()
        job = scope.launch {
            combine(earFlow, prefsStore.observe()) { ear, prefs -> ear to prefs }
                .collectLatest { (ear, prefs) ->
                    if (!prefs.pauseOnRemove) return@collectLatest
                    val bothOut = ear.left == EarPresence.OUT_OF_EAR && ear.right == EarPresence.OUT_OF_EAR
                    if (bothOut && !lastBothOut) {
                        dispatchPause()
                    }
                    lastBothOut = bothOut
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun dispatchPause() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
    }
}
