package com.banya.neulpum.data.remote

import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VoiceWebSocketClient(
    private val url: String,
    private val accessToken: String?,
    private val organizationApiKey: String?,
    private val listener: Listener
) {
    interface Listener {
        fun onOpen()
        fun onTtsChunk(bytes: ByteArray, format: String?)
        fun onTtsChunkStream(bytes: ByteArray, format: String?, chunkIndex: Int, isFinal: Boolean)
        fun onDone()
        fun onError(error: String)
        fun onLog(stage: String, message: String)
        fun onClose(code: Int, reason: String)
        fun onConversationCreated(conversationId: String)
    }

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 원래 설정으로 복원
        .build()

    fun connect() {
        val reqBuilder = Request.Builder().url(url)
        if (!accessToken.isNullOrEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer $accessToken")
        }
        if (!organizationApiKey.isNullOrEmpty()) {
            reqBuilder.addHeader("X-API-Key", organizationApiKey)
        }
        val request = reqBuilder.build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("VoiceWebSocketClient: WebSocket connected")
                android.util.Log.d("VoiceWebSocketClient", "WebSocket connected")
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // 비동기로 메시지 처리하여 메인 스레드 블록 방지
                Thread {
                    try {
                        val json = JSONObject(text)
                        val type = json.optString("type", "")
                        when (type) {
                            "tts_audio" -> {
                                val format = json.optString("format", null)
                                val chunkB64 = json.optString("chunk", "")
                                println("VoiceWebSocketClient: Received TTS audio chunk, format=$format, b64_len=${chunkB64.length}")
                                if (chunkB64.isNotEmpty()) {
                                    val bytes = Base64.decode(chunkB64, Base64.DEFAULT)
                                    println("VoiceWebSocketClient: Decoded TTS audio bytes: ${bytes.size} bytes")
                                    listener.onTtsChunk(bytes, format)
                                } else {
                                    println("VoiceWebSocketClient: Empty TTS chunk received")
                                }
                            }
                            "tts_audio_chunk" -> {
                                val format = json.optString("format", null)
                                val chunkB64 = json.optString("chunk", "")
                                val chunkIndex = json.optInt("chunk_index", 0)
                                val isFinal = json.optBoolean("is_final", false)
                                println("VoiceWebSocketClient: Received streaming TTS chunk $chunkIndex, format=$format, b64_len=${chunkB64.length}, isFinal=$isFinal")
                                if (chunkB64.isNotEmpty()) {
                                    val bytes = Base64.decode(chunkB64, Base64.DEFAULT)
                                    println("VoiceWebSocketClient: Decoded streaming TTS bytes: ${bytes.size} bytes")
                                    listener.onTtsChunkStream(bytes, format, chunkIndex, isFinal)
                                } else if (isFinal) {
                                    println("VoiceWebSocketClient: Final streaming TTS chunk received")
                                    listener.onTtsChunkStream(ByteArray(0), format, chunkIndex, isFinal)
                                } else {
                                    println("VoiceWebSocketClient: Empty streaming TTS chunk received")
                                }
                            }
                            "error" -> {
                                val msg = json.optString("message", "unknown_error")
                                listener.onError(msg)
                            }
                            "log" -> {
                                val stage = json.optString("stage", "")
                                val msg = json.optString("message", "")
                                println("VoiceWebSocketClient: Log received - Stage: $stage, Message: $msg")
                                android.util.Log.d("VoiceWebSocketClient", "Log received - Stage: $stage, Message: $msg")
                                listener.onLog(stage, msg)
                            }
                            "done" -> {
                                println("VoiceWebSocketClient: Done event received")
                                android.util.Log.d("VoiceWebSocketClient", "Done event received")
                                listener.onDone()
                            }
                            "conversation_created" -> {
                                val convId = json.optString("conversation_id", "")
                                if (convId.isNotEmpty()) {
                                    listener.onConversationCreated(convId)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        listener.onError("Invalid message: ${e.message}")
                    }
                }.start()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Not used currently
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Acknowledge server-initiated close and do not send further frames
                try { webSocket.close(code, reason) } catch (_: Exception) {}
                listener.onClose(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("VoiceWebSocketClient: WebSocket failure - ${t.message}")
                listener.onError(t.message ?: "WebSocket error")
            }
        })
    }

    fun sendText(text: String, conversationId: String? = null) {
        try {
            val json = JSONObject()
            json.put("type", "text")
            json.put("text", text)
            if (!conversationId.isNullOrEmpty()) json.put("conversation_id", conversationId)
            val payload = json.toString()
            val sent = webSocket?.send(payload) ?: false
            if (!sent) {
                listener.onError("ws_send_failed")
            }
        } catch (_: Exception) {}
    }

    fun close() {
        // Graceful client close with NORMAL_CLOSURE
        try { webSocket?.close(1000, "client closing") } catch (_: Exception) {}
        webSocket = null
    }
}


