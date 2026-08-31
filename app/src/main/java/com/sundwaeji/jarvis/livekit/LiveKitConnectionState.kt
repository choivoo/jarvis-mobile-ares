package com.sundwaeji.jarvis.livekit

/** The HUD only displays states reported by the real Room connection. */
enum class LiveKitConnectionState(val hudLabel: String) {
    DISCONNECTED("DISCONNECTED"),
    CONNECTING("CONNECTING"),
    CONNECTED("CONNECTED"),
    RECONNECTING("RECONNECTING"),
    ERROR("ERROR"),
}

data class LiveKitStatus(
    val state: LiveKitConnectionState = LiveKitConnectionState.DISCONNECTED,
    val detail: String? = null,
    val retryAttempt: Int = 0,
) {
    val isConnected: Boolean get() = state == LiveKitConnectionState.CONNECTED
}
