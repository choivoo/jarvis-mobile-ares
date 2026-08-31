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
import com.sundwaeji.jarvis.tools.ToolResult
import com.sundwaeji.jarvis.tools.ToolRouter
import com.sundwaeji.jarvis.voice.JarvisVoiceController

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf(JarvisUiState())
    private lateinit var voice: JarvisVoiceController
    private lateinit var translation: TranslationManager
    private lateinit var tools: ToolRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        uiState = uiState.copy(batteryPercent = battery)
        translation = TranslationManager()
        tools = ToolRouter(applicationContext)
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
        tools.close()
        super.onDestroy()
    }

    private fun processKoreanVoice(koreanInput: String) {
        uiState = uiState.copy(phase = JarvisPhase.TRANSLATING, activeTool = "TRANSLATE", koreanSubtitle = "한국어 명령을 처리하고 있습니다…", audioLevel = 0f)
        translation.translateKoToEn(
            text = koreanInput,
            onSuccess = { englishInput ->
                // The English string is deliberately not exposed on the standard HUD.
                tools.route(
                    englishInput = englishInput,
                    onExecuting = { tool -> uiState = uiState.copy(phase = JarvisPhase.EXECUTING, activeTool = tool, koreanSubtitle = "요청한 도구를 실행하고 있습니다…") },
                    onSuccess = ::deliverToolResult,
                    onFailure = ::showTranslationFailure,
                )
            },
            onFailure = ::showTranslationFailure,
        )
    }

    private fun deliverToolResult(result: ToolResult) {
        translation.translateEnToKo(
            text = result.englishResponse,
            onSuccess = { koreanSubtitle ->
                uiState = uiState.copy(phase = JarvisPhase.SPEAKING, activeTool = result.tool, koreanSubtitle = koreanSubtitle, audioLevel = .35f)
                voice.speak(result.englishResponse)
            },
            onFailure = ::showTranslationFailure,
        )
    }

    private fun showTranslationFailure(message: String) {
        uiState = uiState.copy(phase = JarvisPhase.ERROR, activeTool = null, koreanSubtitle = message, audioLevel = 0f)
    }
}
