package com.sundwaeji.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.BatteryManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import com.sundwaeji.jarvis.background.JarvisCoreService
import com.sundwaeji.jarvis.overlay.JarvisSubtitleOverlayService
import com.sundwaeji.jarvis.voice.JarvisVoiceController
import com.sundwaeji.jarvis.livekit.LiveKitConnectionManager
import com.sundwaeji.jarvis.livekit.LiveKitConnectionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf(JarvisUiState())
    private lateinit var voice: JarvisVoiceController
    private lateinit var translation: TranslationManager
    private lateinit var tools: ToolRouter
    private lateinit var liveKit: LiveKitConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        uiState = uiState.copy(batteryPercent = battery)
        refreshOverlayAuthorization()
        translation = TranslationManager()
        tools = ToolRouter(applicationContext)
        liveKit = LiveKitConnectionManager(applicationContext).also { manager ->
            manager.onAgentResponse = ::deliverAgentResponse
        }
        lifecycleScope.launch {
            liveKit.status.collectLatest { status ->
                val phase = when (status.state) {
                    LiveKitConnectionState.RECONNECTING -> JarvisPhase.THINKING
                    LiveKitConnectionState.ERROR -> JarvisPhase.ERROR
                    else -> uiState.phase
                }
                uiState = uiState.copy(
                    phase = phase,
                    networkLabel = status.state.hudLabel,
                )
            }
        }
        liveKit.connect()
        voice = JarvisVoiceController(
            context = this,
            onListening = {
                pauseWakeForConversation()
                uiState = uiState.copy(phase = JarvisPhase.LISTENING, koreanSubtitle = "듣고 있습니다…", activeTool = "VOICE", audioLevel = 0f)
            },
            onAudioLevel = { level -> uiState = uiState.copy(audioLevel = level) },
            onRecognized = ::processKoreanVoice,
            onSpeakingStarted = { uiState = uiState.copy(phase = JarvisPhase.SPEAKING, audioLevel = 0.35f) },
            onSpeakingFinished = {
                resumeWakeAfterConversation()
                uiState = uiState.copy(phase = JarvisPhase.IDLE, activeTool = null, audioLevel = 0f)
            },
            reportError = { message ->
                resumeWakeAfterConversation()
                uiState = uiState.copy(phase = JarvisPhase.ERROR, activeTool = null, koreanSubtitle = message, audioLevel = 0f)
            },
        )
        enableEdgeToEdge()
        setContent {
            val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) voice.startListening()
                else uiState = uiState.copy(phase = JarvisPhase.ERROR, koreanSubtitle = "음성 명령을 위해 마이크 권한을 허용해 주세요.")
            }
            val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) startBackgroundCore()
                else uiState = uiState.copy(phase = JarvisPhase.ERROR, koreanSubtitle = "백그라운드 상태 표시를 위해 알림 권한이 필요합니다.")
            }
            JarvisAresTheme(darkTheme = true, dynamicColor = false) {
                JarvisHud(
                    state = uiState,
                    onMic = {
                        if (uiState.phase == JarvisPhase.LISTENING) {
                            voice.stopListening()
                            resumeWakeAfterConversation()
                        }
                        else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOverlay = ::openOverlayPermission,
                    onCopyActivationId = ::copyActivationId,
                    onSystem = {
                        if (uiState.backgroundServiceRunning) stopBackgroundCore()
                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startBackgroundCore()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        voice.release()
        translation.close()
        tools.close()
        liveKit.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayAuthorization()
    }

    private fun processKoreanVoice(koreanInput: String) {
        uiState = uiState.copy(phase = JarvisPhase.TRANSLATING, activeTool = "TRANSLATE", koreanSubtitle = "한국어 명령을 처리하고 있습니다…", audioLevel = 0f)
        translation.translateKoToEn(
            text = koreanInput,
            onSuccess = { englishInput ->
                // The English string is deliberately not exposed on the standard HUD.
                if (!tools.handlesLocally(englishInput) && liveKit.sendEnglishCommand(englishInput)) {
                    uiState = uiState.copy(phase = JarvisPhase.THINKING, activeTool = "AI", koreanSubtitle = "JARVIS가 요청을 분석하고 있습니다…")
                } else {
                    tools.route(
                        englishInput = englishInput,
                        onExecuting = { tool -> uiState = uiState.copy(phase = JarvisPhase.EXECUTING, activeTool = tool, koreanSubtitle = "요청한 도구를 실행하고 있습니다…") },
                        onSuccess = ::deliverToolResult,
                        onFailure = ::showTranslationFailure,
                    )
                }
            },
            onFailure = ::showTranslationFailure,
        )
    }

    private fun deliverToolResult(result: ToolResult) {
        deliverEnglishResponse(result.englishResponse, result.tool, shouldSpeakLocally = true)
    }

    private fun deliverAgentResponse(englishResponse: String) {
        // Gemini Live already provides the English audio through the subscribed Room track.
        deliverEnglishResponse(englishResponse, "AI", shouldSpeakLocally = false)
    }

    private fun deliverEnglishResponse(englishResponse: String, tool: String, shouldSpeakLocally: Boolean) {
        translation.translateEnToKo(
            text = englishResponse,
            onSuccess = { koreanSubtitle ->
                uiState = uiState.copy(phase = JarvisPhase.SPEAKING, activeTool = tool, koreanSubtitle = koreanSubtitle, audioLevel = .35f)
                showOverlaySubtitle(koreanSubtitle)
                if (shouldSpeakLocally) voice.speak(englishResponse)
            },
            onFailure = ::showTranslationFailure,
        )
    }

    private fun showTranslationFailure(message: String) {
        uiState = uiState.copy(phase = JarvisPhase.ERROR, activeTool = null, koreanSubtitle = message, audioLevel = 0f)
    }

    private fun startBackgroundCore() {
        ContextCompat.startForegroundService(this, Intent(this, JarvisCoreService::class.java))
        uiState = uiState.copy(backgroundServiceRunning = true, koreanSubtitle = "JARVIS 백그라운드 시스템을 시작했습니다.")
    }

    private fun stopBackgroundCore() {
        stopService(Intent(this, JarvisCoreService::class.java))
        uiState = uiState.copy(backgroundServiceRunning = false, koreanSubtitle = "JARVIS 백그라운드 시스템을 중지했습니다.")
    }

    private fun pauseWakeForConversation() {
        if (!uiState.backgroundServiceRunning) return
        startService(Intent(this, JarvisCoreService::class.java).setAction(JarvisCoreService.ACTION_PAUSE_WAKE))
    }

    private fun resumeWakeAfterConversation() {
        if (!uiState.backgroundServiceRunning) return
        startService(Intent(this, JarvisCoreService::class.java).setAction(JarvisCoreService.ACTION_RESUME_WAKE))
    }

    private fun openOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            uiState = uiState.copy(overlayAuthorized = true, koreanSubtitle = "다른 앱 위 한국어 자막이 활성화되어 있습니다.")
            return
        }
        uiState = uiState.copy(koreanSubtitle = "다른 앱에서도 한국어 자막을 표시하려면 '다른 앱 위에 표시' 권한을 허용해 주세요.")
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun refreshOverlayAuthorization() {
        uiState = uiState.copy(overlayAuthorized = Settings.canDrawOverlays(this))
    }

    private fun showOverlaySubtitle(text: String) {
        if (!Settings.canDrawOverlays(this)) return
        startService(
            Intent(this, JarvisSubtitleOverlayService::class.java)
                .putExtra(JarvisSubtitleOverlayService.EXTRA_SUBTITLE, text),
        )
    }

    private fun copyActivationId() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS activation ID", liveKit.installationId))
        uiState = uiState.copy(koreanSubtitle = "기기 활성화 ID를 복사했습니다. 배포 allowlist 등록에만 사용하세요.")
    }
}
