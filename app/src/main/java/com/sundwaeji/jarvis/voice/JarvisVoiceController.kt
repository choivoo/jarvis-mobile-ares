package com.sundwaeji.jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * V0.2 local voice bridge. Speech recognition stays on the device/provider selected
 * by Android; no microphone content is sent to a JARVIS cloud service at this stage.
 */
class JarvisVoiceController(
    context: Context,
    private val onListening: () -> Unit,
    private val onAudioLevel: (Float) -> Unit,
    private val onRecognized: (String) -> Unit,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingFinished: () -> Unit,
    private val reportError: (String) -> Unit,
) : RecognitionListener {
    private val appContext = context.applicationContext
    private var isListening = false
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                tts?.setSpeechRate(0.94f)
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onSpeakingStarted()
                    override fun onDone(utteranceId: String?) = onSpeakingFinished()
                    override fun onError(utteranceId: String?) = reportError("영어 음성 재생을 시작하지 못했습니다.")
                })
            }
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            reportError("이 기기에서는 음성 인식을 사용할 수 없습니다.")
            return
        }
        stopSpeaking()
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { it.setRecognitionListener(this) }
        }
        isListening = true
        onListening()
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    fun stopListening() {
        if (isListening) recognizer?.stopListening()
        isListening = false
        onAudioLevel(0f)
    }

    fun speak(text: String) {
        val engine = tts ?: run {
            reportError("영어 음성 엔진을 준비 중입니다. 잠시 후 다시 시도하세요.")
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "jarvis-response")
    }

    fun stopSpeaking() = tts?.stop()

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { isListening = false; onAudioLevel(0f) }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onRmsChanged(rmsdB: Float) {
        onAudioLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
    }

    override fun onResults(results: Bundle?) {
        val koreanText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (koreanText.isNullOrBlank()) reportError("음성을 인식하지 못했습니다. 다시 말씀해 주세요.") else onRecognized(koreanText)
    }

    override fun onError(error: Int) {
        isListening = false
        onAudioLevel(0f)
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "마이크 입력에 문제가 있습니다."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식 네트워크 연결에 문제가 있습니다."
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성을 인식하지 못했습니다. 다시 말씀해 주세요."
            else -> "음성 인식이 중단되었습니다. 다시 시도하세요."
        }
        reportError(message)
    }
}
