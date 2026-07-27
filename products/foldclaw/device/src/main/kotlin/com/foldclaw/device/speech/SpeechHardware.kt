package com.foldclaw.device.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import com.foldclaw.domain.speech.TtsSpeaker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    @Synchronized
    fun start(): File {
        stopQuietly()
        val file = File(context.cacheDir, "foldclaw_voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(16_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        output = file
        return file
    }

    @Synchronized
    fun stop(): File? {
        val file = output
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop recorder", e)
        }
        recorder = null
        output = null
        return file
    }

    @Synchronized
    fun cancel() {
        try {
            recorder?.apply {
                runCatching { stop() }
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        output?.delete()
        output = null
    }

    private fun stopQuietly() {
        try {
            recorder?.apply {
                runCatching { stop() }
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
    }

    companion object {
        private const val TAG = "FoldClaw/Rec"
    }
}

@Singleton
class AndroidTtsSpeaker @Inject constructor(
    @ApplicationContext context: Context,
) : TtsSpeaker {
    private val ready = AtomicBoolean(false)
    private val tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.SIMPLIFIED_CHINESE
                ready.set(true)
            } else {
                Log.w(TAG, "TTS init failed: $status")
            }
        }
    }

    override fun speak(text: String) {
        val clean = text.trim().take(200)
        if (clean.isEmpty()) return
        if (!ready.get()) {
            Log.w(TAG, "TTS not ready")
            return
        }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "foldclaw_${System.currentTimeMillis()}")
    }

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    companion object {
        private const val TAG = "FoldClaw/TTS"
    }
}
