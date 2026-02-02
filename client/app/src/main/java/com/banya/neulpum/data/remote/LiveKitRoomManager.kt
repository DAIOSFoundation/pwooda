package com.banya.neulpum.data.remote

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Represents the current state of the LiveKit room connection.
 */
sealed class RoomState {
    object Disconnected : RoomState()
    object Connecting : RoomState()
    object Connected : RoomState()
    object Reconnecting : RoomState()
    data class Error(val message: String) : RoomState()
}

/**
 * Data received from the AI agent via Data Channel.
 */
sealed class AgentMessage {
    data class Text(val content: String) : AgentMessage()
    data class Status(val status: String) : AgentMessage()
    data class Error(val message: String) : AgentMessage()
}

/**
 * Manages the LiveKit room connection and communication with the AI agent.
 *
 * Features:
 * - Room connection/disconnection
 * - Microphone publishing (disabled by default, using client-side STT)
 * - AI audio track subscription
 * - Data Channel communication (text messages, interrupts)
 */
class LiveKitRoomManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "LiveKitRoomManager"
    }

    private var room: Room? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State flows
    private val _roomState = MutableStateFlow<RoomState>(RoomState.Disconnected)
    val roomState: StateFlow<RoomState> = _roomState.asStateFlow()

    private val _aiAudioLevel = MutableStateFlow(0f)
    val aiAudioLevel: StateFlow<Float> = _aiAudioLevel.asStateFlow()

    private val _agentMessages = MutableSharedFlow<AgentMessage>()
    val agentMessages: SharedFlow<AgentMessage> = _agentMessages.asSharedFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /**
     * Connect to a LiveKit room.
     *
     * @param url LiveKit server URL (ws:// or wss://)
     * @param token Access token from token server
     * @param enableMicrophone Whether to enable microphone (false for client-side STT)
     */
    suspend fun connect(
        url: String,
        token: String,
        enableMicrophone: Boolean = false
    ): Result<Unit> {
        return try {
            _roomState.value = RoomState.Connecting

            // Create room instance
            room = LiveKit.create(context)

            // Set up event handlers
            setupEventHandlers()

            // Connect to room
            room?.connect(url, token)

            // Optionally enable microphone (disabled for client-side STT approach)
            if (enableMicrophone) {
                room?.localParticipant?.setMicrophoneEnabled(true)
            }

            _roomState.value = RoomState.Connected
            Log.d(TAG, "Connected to room")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _roomState.value = RoomState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Set up room event handlers.
     */
    private fun setupEventHandlers() {
        scope.launch {
            room?.events?.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> onTrackSubscribed(event)
                    is RoomEvent.TrackUnsubscribed -> onTrackUnsubscribed(event)
                    is RoomEvent.DataReceived -> onDataReceived(event)
                    is RoomEvent.Disconnected -> onDisconnected()
                    is RoomEvent.Reconnecting -> onReconnecting()
                    is RoomEvent.Reconnected -> onReconnected()
                    is RoomEvent.ParticipantConnected -> onParticipantConnected(event)
                    is RoomEvent.ParticipantDisconnected -> onParticipantDisconnected(event)
                    else -> {}
                }
            }
        }
    }

    private fun onTrackSubscribed(event: RoomEvent.TrackSubscribed) {
        val track = event.track
        if (track is RemoteAudioTrack) {
            Log.d(TAG, "Subscribed to AI audio track")
            _isSpeaking.value = true

            // Monitor audio level
            monitorAudioLevel(track)
        }
    }

    private fun onTrackUnsubscribed(event: RoomEvent.TrackUnsubscribed) {
        if (event.track.kind == Track.Kind.AUDIO) {
            Log.d(TAG, "AI audio track unsubscribed")
            _isSpeaking.value = false
            _aiAudioLevel.value = 0f
        }
    }

    private fun monitorAudioLevel(track: RemoteAudioTrack) {
        scope.launch {
            while (isActive && _roomState.value == RoomState.Connected) {
                // LiveKit provides audio level through the track
                // This is a simplified version - real implementation may vary
                delay(50) // Update every 50ms
            }
        }
    }

    private fun onDataReceived(event: RoomEvent.DataReceived) {
        try {
            val dataString = String(event.data, StandardCharsets.UTF_8)
            val json = JSONObject(dataString)

            when (json.optString("type")) {
                "text" -> {
                    val content = json.optString("content", "")
                    scope.launch {
                        _agentMessages.emit(AgentMessage.Text(content))
                    }
                }
                "status" -> {
                    val status = json.optString("status", "")
                    scope.launch {
                        _agentMessages.emit(AgentMessage.Status(status))
                    }

                    // Update speaking state based on status
                    when (status) {
                        "processing" -> _isSpeaking.value = true
                        "done" -> _isSpeaking.value = false
                    }
                }
                "error" -> {
                    val message = json.optString("message", "Unknown error")
                    scope.launch {
                        _agentMessages.emit(AgentMessage.Error(message))
                    }
                }
                "pong" -> {
                    Log.d(TAG, "Pong received")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data", e)
        }
    }

    private fun onDisconnected() {
        Log.d(TAG, "Disconnected from room")
        _roomState.value = RoomState.Disconnected
        _isSpeaking.value = false
        _aiAudioLevel.value = 0f
    }

    private fun onReconnecting() {
        Log.d(TAG, "Reconnecting...")
        _roomState.value = RoomState.Reconnecting
    }

    private fun onReconnected() {
        Log.d(TAG, "Reconnected")
        _roomState.value = RoomState.Connected
    }

    private fun onParticipantConnected(event: RoomEvent.ParticipantConnected) {
        Log.d(TAG, "Participant connected: ${event.participant.identity}")
    }

    private fun onParticipantDisconnected(event: RoomEvent.ParticipantDisconnected) {
        Log.d(TAG, "Participant disconnected: ${event.participant.identity}")
    }

    /**
     * Send a text message to the AI agent via Data Channel.
     * This is used for client-side STT results.
     *
     * @param text The transcribed text from speech recognition
     */
    fun sendTextMessage(text: String) {
        scope.launch {
            try {
                val data = JSONObject().apply {
                    put("type", "text")
                    put("content", text)
                }.toString().toByteArray(StandardCharsets.UTF_8)

                room?.localParticipant?.publishData(data)
                Log.d(TAG, "Text message sent: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending text message", e)
            }
        }
    }

    /**
     * Send an interrupt signal to stop the AI's current response.
     */
    fun sendInterrupt() {
        scope.launch {
            try {
                val data = JSONObject().apply {
                    put("type", "interrupt")
                }.toString().toByteArray(StandardCharsets.UTF_8)

                room?.localParticipant?.publishData(data)
                Log.d(TAG, "Interrupt signal sent")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending interrupt", e)
            }
        }
    }

    /**
     * Send a ping to keep the connection alive.
     */
    fun sendPing() {
        scope.launch {
            try {
                val data = JSONObject().apply {
                    put("type", "ping")
                }.toString().toByteArray(StandardCharsets.UTF_8)

                room?.localParticipant?.publishData(data)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending ping", e)
            }
        }
    }

    /**
     * Enable or disable the local microphone.
     * Note: For client-side STT approach, this may not be needed.
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        scope.launch {
            room?.localParticipant?.setMicrophoneEnabled(enabled)
        }
    }

    /**
     * Disconnect from the room.
     */
    fun disconnect() {
        scope.launch {
            room?.disconnect()
            room?.release()
            room = null
            _roomState.value = RoomState.Disconnected
            _isSpeaking.value = false
            _aiAudioLevel.value = 0f
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        disconnect()
        scope.cancel()
    }
}
