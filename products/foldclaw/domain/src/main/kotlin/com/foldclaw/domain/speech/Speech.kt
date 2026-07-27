package com.foldclaw.domain.speech

import com.foldclaw.domain.model.Result
import java.io.File

interface SpeechAsrClient {
    suspend fun transcribe(audioFile: File): Result<String>
}

interface TtsSpeaker {
    fun speak(text: String)
    fun stop()
    fun shutdown()
}
