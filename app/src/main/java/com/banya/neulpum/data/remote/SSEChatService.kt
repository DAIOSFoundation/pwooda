
package com.banya.neulpum.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.*
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.banya.neulpum.di.AppConfig

class SSEChatService {
    
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // SSE는 무제한 읽기
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    fun chatSSE(
        message: String,
        organizationApiKey: String? = null,
        accessToken: String? = null,
        providerId: String? = null,
        conversationId: String? = null,
        imageBase64: String? = null
    ): Flow<ChatSSEEvent> = callbackFlow {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api-llmops.banya.ai")
            .addPathSegments("api/v1/chat/sse")
            .build()
        
        println("SSE URL: $url")
        
        // 이미지가 있으면 POST 요청으로 변경
        val requestBuilder = if (imageBase64 != null) {
            val requestBody = FormBody.Builder()
                .add("message", message)
                .apply {
                    providerId?.let { add("provider_id", it) }
                    conversationId?.let { add("conversation_id", it) }
                    add("image_base64", imageBase64)
                }
                .build()
            
            Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
        } else {
            val urlWithParams = url.newBuilder()
                .addQueryParameter("message", message)
                .apply {
                    providerId?.let { addQueryParameter("provider_id", it) }
                    conversationId?.let { addQueryParameter("conversation_id", it) }
                }
                .build()
            
            Request.Builder()
                .url(urlWithParams)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
        }
        
        organizationApiKey?.let {
            requestBuilder.addHeader("X-API-Key", it)
        }
        
        accessToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        val request = requestBuilder.build()
        
        println("SSE Headers: ${request.headers}")
        println("SSE Message: $message")
        
        try {
            println("Starting SSE request...")
            
            // okhttp-sse를 사용한 실시간 스트림 처리
            val sseClient = EventSources.createFactory(client)
            val eventSource = sseClient.newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    println("SSE Connection opened: ${response.code}")
                }
                
                                        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                            println("[$timestamp] SSE Event - ID: $id, Type: $type, Data: $data")

                            try {
                                val event = parseSSEEvent(data)
                                println("[$timestamp] SSE Parsed Event: $event")
                                trySend(event)
                            } catch (e: Exception) {
                                // JSON 파싱 에러는 조용히 넘어감
                                trySend(ChatSSEEvent.Error("Parse error: ${e.message}"))
                            }
                        }
                
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    println("SSE Connection failed: ${t?.message}")
                    trySend(ChatSSEEvent.Error("Connection failed: ${t?.message ?: "Unknown error"}"))
                    close()
                }
                
                override fun onClosed(eventSource: EventSource) {
                    println("SSE Connection closed")
                    close()
                }
            })
            
            // 연결이 유지되도록 대기
            while (!isClosedForSend) {
                delay(100)
            }
            
        } catch (e: Exception) {
            println("SSE Request error: ${e.message}")
            println("SSE Request error type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            trySend(ChatSSEEvent.Error("Request error: ${e.message ?: "Unknown error"}"))
            close()
        }
    }
    
    private fun parseSSEEvent(payload: String): ChatSSEEvent {
        fun safeGetString(obj: JsonObject, key: String): String? {
            val el = obj.get(key) ?: return null
            if (el.isJsonNull) return null
            return try {
                if (el.isJsonPrimitive) el.asString else el.toString()
            } catch (_: Exception) {
                el.toString()
            }
        }

        return try {
            // Gson을 사용한 JSON 파싱
            val jsonObject = gson.fromJson(payload, JsonObject::class.java)
            val eventType = safeGetString(jsonObject, "event")
            
            when (eventType) {
                "step" -> {
                    val stage = safeGetString(jsonObject, "stage") ?: "unknown"
                    val detail = safeGetString(jsonObject, "detail") ?: ""
                    val tool = safeGetString(jsonObject, "tool")
                    
                    // result는 복잡한 JSON 객체일 수 있으므로 toString()으로 처리
                    val result = jsonObject.get("result")?.let { resultElement ->
                        if (resultElement.isJsonNull) {
                            null
                        } else if (resultElement.isJsonPrimitive) {
                            try { resultElement.asString } catch (_: Exception) { resultElement.toString() }
                        } else {
                            resultElement.toString()
                        }
                    }
                    
                    ChatSSEEvent.Step(
                        stage = stage,
                        detail = detail,
                        tool = tool,
                        result = result
                    )
                }
                "final" -> {
                    val result = safeGetString(jsonObject, "result") ?: ""
                    val convId = safeGetString(jsonObject, "conversation_id")
                    val error = safeGetString(jsonObject, "error")
                    
                    if (error != null) {
                        ChatSSEEvent.Error(error)
                    } else {
                        ChatSSEEvent.Final(result, convId)
                    }
                }
                "error" -> {
                    val error = safeGetString(jsonObject, "error") ?: "Unknown error"
                    ChatSSEEvent.Error(error)
                }
                else -> {
                    ChatSSEEvent.Unknown(payload)
                }
            }
        } catch (e: Exception) {
            // JSON 파싱 에러는 조용히 처리
            ChatSSEEvent.Error("Parse error: ${e.message}")
        }
    }
    
    private fun extractJsonValue(json: String, key: String): String? {
        // 더 정확한 JSON 파싱을 위한 정규식 - 여러 패턴 시도
        val patterns = listOf(
            "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex(),
            "\"$key\"\\s*:\\s*\"([^\"]*?)\"".toRegex(),
            "\"$key\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"".toRegex()
        )
        
        for (pattern in patterns) {
            val result = pattern.find(json)?.groupValues?.get(1)
            if (result != null) {
                println("Extracting '$key' from '$json': $result")
                return result
            }
        }
        
        println("Failed to extract '$key' from '$json'")
        return null
    }
}

sealed class ChatSSEEvent {
    data class Step(
        val stage: String,
        val detail: String,
        val tool: String? = null,
        val result: String? = null
    ) : ChatSSEEvent()
    
    data class Final(val result: String, val conversationId: String? = null) : ChatSSEEvent()
    data class Error(val message: String) : ChatSSEEvent()
    data class Unknown(val raw: String) : ChatSSEEvent()
}

