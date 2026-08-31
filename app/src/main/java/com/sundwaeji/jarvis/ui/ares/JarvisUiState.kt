package com.sundwaeji.jarvis.ui.ares

enum class JarvisPhase(val label: String) {
    BOOTING("INITIALIZING"), IDLE("ONLINE"), WAKE_DETECTED("AWAKE"), LISTENING("LISTENING"),
    TRANSLATING("TRANSLATING"), THINKING("ANALYZING"), EXECUTING("EXECUTING"),
    SPEAKING("SPEAKING"), OFFLINE("LOCAL MODE"), ERROR("DEGRADED")
}

data class JarvisUiState(
    val phase: JarvisPhase = JarvisPhase.IDLE,
    val koreanSubtitle: String = "마이크를 눌러 JARVIS를 시작하세요.",
    val activeTool: String? = null,
    val networkLabel: String = "LOCAL",
    val batteryPercent: Int? = null,
    val audioLevel: Float = 0f,
    val backgroundServiceRunning: Boolean = false,
    val overlayAuthorized: Boolean = false,
)
