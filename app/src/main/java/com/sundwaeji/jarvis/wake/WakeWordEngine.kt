package com.sundwaeji.jarvis.wake

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

enum class WakeEngineState { STOPPED, STARTING, LISTENING, DETECTED, UNAVAILABLE, ERROR }

/**
 * Local-only best-effort wake detector. It deliberately never falls back to the cloud
 * SpeechRecognizer: if the device has no Korean on-device recognizer, it reports
 * UNAVAILABLE and asks the foreground UI to offer manual voice input instead.
 */
class WakeWordEngine(
    context: Context,
    private val onState: (WakeEngineState, String?) -> Unit,
    private val onWakeDetected: () -> Unit,
) : RecognitionListener {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var cooldownUntilMs = 0L

    fun start() {
        if (running) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            onState(WakeEngineState.UNAVAILABLE, "On-device Korean speech recognition is unavailable")
            return
        }
        running = true
        onState(WakeEngineState.STARTING, null)
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext).also { it.setRecognitionListener(this) }
        beginRecognition()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        onState(WakeEngineState.STOPPED, null)
    }

    private fun beginRecognition() {
        if (!running) return
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
        onState(WakeEngineState.LISTENING, null)
    }

    private fun examine(results: Bundle?) {
        val hit = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.any { normalize(it).contains("자비스") || normalize(it).contains("jarvis") } == true
        if (!hit || !running || System.currentTimeMillis() < cooldownUntilMs) return
        cooldownUntilMs = System.currentTimeMillis() + COOLDOWN_MS
        running = false // release microphone before conversation STT acquires it.
        recognizer?.cancel()
        onState(WakeEngineState.DETECTED, null)
        onWakeDetected()
    }

    private fun restart(delayMs: Long = RESTART_DELAY_MS) {
        if (running) main.postDelayed({ beginRecognition() }, delayMs)
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(" ", "")

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = examine(partialResults)
    override fun onResults(results: Bundle?) { examine(results); restart() }
    override fun onError(error: Int) {
        if (!running) return
        // BUSY and speech timeout are expected for a continuous, local keyword loop.
        val detail = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) "Recognizer busy; retrying" else null
        onState(if (detail == null) WakeEngineState.ERROR else WakeEngineState.LISTENING, detail)
        restart()
    }

    companion object {
        private const val RESTART_DELAY_MS = 450L
        private const val COOLDOWN_MS = 2_000L
    }
}
