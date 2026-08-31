package com.sundwaeji.jarvis.livekit

import android.content.Context
import com.sundwaeji.jarvis.BuildConfig
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A real LiveKit Room client. It receives a short-lived token from a HTTPS endpoint;
 * the endpoint is public configuration only and LiveKit credentials never enter the APK.
 *
 * Korean is deliberately sent to the agent as already translated English text on a data
 * topic. Raw microphone publication stays off until a server-side translation stream is
 * enabled, preventing Korean audio from bypassing the KO -> EN privacy/language contract.
 */
class LiveKitConnectionManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val room: Room = LiveKit.create(appContext)
    private var eventsJob: Job? = null
    private var reconnectJob: Job? = null
    private var explicitlyClosed = false
    private val installationId = appContext
        .getSharedPreferences("jarvis_livekit", Context.MODE_PRIVATE)
        .getString("installation_id", null)
        ?: UUID.randomUUID().toString().also {
            appContext.getSharedPreferences("jarvis_livekit", Context.MODE_PRIVATE)
                .edit().putString("installation_id", it).apply()
        }

    private val _status = MutableStateFlow(LiveKitStatus())
    val status: StateFlow<LiveKitStatus> = _status.asStateFlow()

    var onAgentResponse: ((String) -> Unit)? = null

    init {
        eventsJob = scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> update(LiveKitConnectionState.CONNECTED)
                    is RoomEvent.Reconnecting -> update(LiveKitConnectionState.RECONNECTING)
                    is RoomEvent.Reconnected -> update(LiveKitConnectionState.CONNECTED)
                    is RoomEvent.Disconnected -> {
                        update(LiveKitConnectionState.DISCONNECTED, event.error?.message)
                        scheduleReconnect(event.error?.message)
                    }
                    is RoomEvent.FailedToConnect -> {
                        update(LiveKitConnectionState.ERROR, event.error.message)
                        scheduleReconnect(event.error.message)
                    }
                    is RoomEvent.DataReceived -> receiveAgentData(event.topic, event.data)
                    else -> Unit
                }
            }
        }
    }

    fun connect() {
        if (BuildConfig.JARVIS_TOKEN_ENDPOINT.isBlank()) {
            update(LiveKitConnectionState.DISCONNECTED, "Secure token endpoint is not configured")
            return
        }
        if (status.value.state == LiveKitConnectionState.CONNECTING || status.value.isConnected) return
        explicitlyClosed = false
        reconnectJob?.cancel()
        scope.launch { connectOnce() }
    }

    fun sendEnglishCommand(englishInput: String): Boolean {
        if (!status.value.isConnected || englishInput.isBlank()) return false
        scope.launch {
            val payload = JSONObject().put("text", englishInput).put("schema", 1).toString()
            val published = room.localParticipant.publishData(
                payload.toByteArray(Charsets.UTF_8),
                DataPublishReliability.RELIABLE,
                TOPIC_ENGLISH_COMMAND,
            )
            if (published.isFailure) update(LiveKitConnectionState.ERROR, "Could not deliver command")
        }
        return true
    }

    /** Explicit ownership hand-off only; never enable this while Android STT owns the mic. */
    fun setMicrophoneStreaming(enabled: Boolean) {
        if (!status.value.isConnected) return
        scope.launch { room.localParticipant.setMicrophoneEnabled(enabled) }
    }

    fun disconnect() {
        explicitlyClosed = true
        reconnectJob?.cancel()
        room.disconnect()
        update(LiveKitConnectionState.DISCONNECTED)
    }

    fun release() {
        disconnect()
        eventsJob?.cancel()
        room.release()
        http.dispatcher.executorService.shutdown()
        scope.cancel()
    }

    private suspend fun connectOnce() {
        update(LiveKitConnectionState.CONNECTING)
        runCatching {
            val token = fetchToken()
            room.connect(token.serverUrl, token.participantToken)
            // Android STT owns the microphone for the required KO -> EN pipeline.
            room.localParticipant.setMicrophoneEnabled(false)
        }.onFailure { error ->
            update(LiveKitConnectionState.ERROR, error.message ?: "LiveKit connection failed")
            scheduleReconnect(error.message)
        }
    }

    private fun fetchToken(): RoomToken {
        val endpoint = BuildConfig.JARVIS_TOKEN_ENDPOINT
        require(endpoint.startsWith("https://")) { "Token endpoint must use HTTPS" }
        val request = Request.Builder()
            .url(endpoint)
            .header("X-Jarvis-Installation", installationId)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Token service returned ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            return RoomToken(
                serverUrl = json.getString("serverUrl"),
                participantToken = json.getString("participantToken"),
            )
        }
    }

    private fun receiveAgentData(topic: String?, data: ByteArray) {
        if (topic != TOPIC_AGENT_RESPONSE) return
        val text = runCatching { JSONObject(data.toString(Charsets.UTF_8)).optString("english") }.getOrNull()
        if (!text.isNullOrBlank()) onAgentResponse?.invoke(text)
    }

    private fun scheduleReconnect(reason: String?) {
        if (explicitlyClosed || BuildConfig.JARVIS_TOKEN_ENDPOINT.isBlank()) return
        if (reconnectJob?.isActive == true) return
        val nextAttempt = (status.value.retryAttempt + 1).coerceAtMost(MAX_RETRIES)
        if (nextAttempt >= MAX_RETRIES) {
            update(LiveKitConnectionState.ERROR, reason ?: "Reconnect limit reached", nextAttempt)
            return
        }
        reconnectJob = scope.launch {
            update(LiveKitConnectionState.RECONNECTING, reason, nextAttempt)
            delay((1L shl nextAttempt) * 1_000L)
            connectOnce()
        }
    }

    private fun update(state: LiveKitConnectionState, detail: String? = null, retries: Int = status.value.retryAttempt) {
        _status.value = LiveKitStatus(state, detail, retries)
    }

    private data class RoomToken(val serverUrl: String, val participantToken: String)

    companion object {
        const val TOPIC_ENGLISH_COMMAND = "jarvis.command.en.v1"
        const val TOPIC_AGENT_RESPONSE = "jarvis.response.en.v1"
        private const val MAX_RETRIES = 4
    }
}
