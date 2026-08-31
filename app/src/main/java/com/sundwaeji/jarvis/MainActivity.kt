package com.sundwaeji.jarvis

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.os.BatteryManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sundwaeji.jarvis.ui.ares.JarvisHud
import com.sundwaeji.jarvis.ui.ares.JarvisPhase
import com.sundwaeji.jarvis.ui.ares.JarvisUiState
import com.sundwaeji.jarvis.ui.theme.JarvisAresTheme
import com.sundwaeji.jarvis.translation.TranslationManager
import com.sundwaeji.jarvis.voice.JarvisVoiceController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf(JarvisUiState())
    private lateinit var voice: JarvisVoiceController
    private lateinit var translation: TranslationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        uiState = uiState.copy(batteryPercent = battery)
        translation = TranslationManager()
        voice = JarvisVoiceController(
            context = this,
            onListening = { uiState = uiState.copy(phase = JarvisPhase.LISTENING, koreanSubtitle = "듣고 있습니다…", activeTool = "VOICE", audioLevel = 0f) },
            onAudioLevel = { level -> uiState = uiState.copy(audioLevel = level) },
            onRecognized = ::processKoreanVoice,
            onSpeakingStarted = { uiState = uiState.copy(phase = JarvisPhase.SPEAKING, audioLevel = 0.35f) },
            onSpeakingFinished = { uiState = uiState.copy(phase = JarvisPhase.IDLE, activeTool = null, audioLevel = 0f) },
            reportError = { message -> uiState = uiState.copy(phase = JarvisPhase.ERROR, activeTool = null, koreanSubtitle = message, audioLevel = 0f) },
        )
        enableEdgeToEdge()
        setContent {
            val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) voice.startListening()
                else uiState = uiState.copy(phase = JarvisPhase.ERROR, koreanSubtitle = "음성 명령을 위해 마이크 권한을 허용해 주세요.")
            }
            JarvisAresTheme(darkTheme = true, dynamicColor = false) {
                JarvisHud(state = uiState) {
                    if (uiState.phase == JarvisPhase.LISTENING) voice.stopListening()
                    else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    override fun onDestroy() {
        voice.release()
        translation.close()
        super.onDestroy()
    }

    private fun processKoreanVoice(koreanInput: String) {
        uiState = uiState.copy(phase = JarvisPhase.TRANSLATING, activeTool = "TRANSLATE", koreanSubtitle = "한국어 명령을 처리하고 있습니다…", audioLevel = 0f)
        translation.translateKoToEn(
            text = koreanInput,
            onSuccess = { englishInput ->
                // The English string is deliberately not exposed on the standard HUD.
                val englishResponse = localEnglishResponse(englishInput)
                translation.translateEnToKo(
                    text = englishResponse,
                    onSuccess = { koreanSubtitle ->
                        uiState = uiState.copy(phase = JarvisPhase.SPEAKING, activeTool = if (englishInput.contains("battery", true) || englishInput.contains("time", true)) "DEVICE" else "LOCAL", koreanSubtitle = koreanSubtitle, audioLevel = .35f)
                        voice.speak(englishResponse)
                    },
                    onFailure = ::showTranslationFailure,
                )
            },
            onFailure = ::showTranslationFailure,
        )
    }

    private fun localEnglishResponse(englishInput: String): String = when {
        englishInput.contains("battery", ignoreCase = true) -> uiState.batteryPercent?.let {
            "Your battery is currently at $it percent."
        } ?: "Battery information is currently unavailable."
        englishInput.contains("time", ignoreCase = true) ->
            "It is currently ${SimpleDateFormat("h:mm a", Locale.UK).format(Date())}."
        else -> "I understand. The cloud intelligence link is being prepared."
    }

    private fun showTranslationFailure(message: String) {
        uiState = uiState.copy(phase = JarvisPhase.ERROR, activeTool = null, koreanSubtitle = message, audioLevel = 0f)
    }
}
