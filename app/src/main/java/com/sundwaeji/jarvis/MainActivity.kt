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
import com.sundwaeji.jarvis.voice.JarvisVoiceController

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf(JarvisUiState())
    private lateinit var voice: JarvisVoiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        uiState = uiState.copy(batteryPercent = battery)
        voice = JarvisVoiceController(
            context = this,
            onListening = { uiState = uiState.copy(phase = JarvisPhase.LISTENING, koreanSubtitle = "듣고 있습니다…", audioLevel = 0f) },
            onAudioLevel = { level -> uiState = uiState.copy(audioLevel = level) },
            onRecognized = { korean ->
                // V0.2 proves the live voice bridge. V0.3 adds KO → EN → AI routing.
                uiState = uiState.copy(
                    phase = JarvisPhase.SPEAKING,
                    audioLevel = 0.35f,
                    koreanSubtitle = "음성을 인식했습니다. 언어 처리 시스템을 준비하고 있습니다.",
                )
                voice.speak("I heard you. The language processing system will be ready in the next update.")
            },
            onSpeakingStarted = { uiState = uiState.copy(phase = JarvisPhase.SPEAKING, audioLevel = 0.35f) },
            onSpeakingFinished = { uiState = uiState.copy(phase = JarvisPhase.IDLE, audioLevel = 0f) },
            reportError = { message -> uiState = uiState.copy(phase = JarvisPhase.ERROR, koreanSubtitle = message, audioLevel = 0f) },
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
        super.onDestroy()
    }
}
