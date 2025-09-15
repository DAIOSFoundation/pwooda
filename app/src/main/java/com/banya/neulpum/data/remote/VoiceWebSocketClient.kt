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
        fun onDone()
        fun onError(error: String)
        fun onLog(stage: String, message: String)
        fun onClose(code: Int, reason: String)
    }

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
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
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type", "")
                    when (type) {
                        "tts_audio" -> {
                            val format = json.optString("format", null)
                            val chunkB64 = json.optString("chunk", "")
                            if (chunkB64.isNotEmpty()) {
                                val bytes = Base64.decode(chunkB64, Base64.DEFAULT)
                                listener.onTtsChunk(bytes, format)
                            }
                        }
                        "error" -> {
                            val msg = json.optString("message", "unknown_error")
                            listener.onError(msg)
                        }
                        "log" -> {
                            val stage = json.optString("stage", "")
                            val msg = json.optString("message", "")
                            listener.onLog(stage, msg)
                        }
                        "done" -> listener.onDone()
                    }
                } catch (e: Exception) {
                    listener.onError("Invalid message: ${e.message}")
                }
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


