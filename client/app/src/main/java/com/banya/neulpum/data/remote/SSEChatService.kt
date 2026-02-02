
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
        try {
            val baseUrl = AppConfig.BASE_HOST
            val cleanUrl = baseUrl.replace(Regex("^https?://"), "")
            val hostAndPort = cleanUrl.split(":")
            val host = hostAndPort[0]
            val port = if (hostAndPort.size > 1) hostAndPort[1].toInt() else if (baseUrl.startsWith("https")) 443 else 80
            
            val url = HttpUrl.Builder()
                .scheme(if (baseUrl.startsWith("https")) "https" else "http")
                .host(host)
                .port(port)
                .addPathSegments("api/v1/chat/sse")
                .build()
        println("SSE Conversation ID: $conversationId")
        
        // 모든 요청을 POST로 변경 (GET 요청에서 문제 발생)
        val requestBody = FormBody.Builder()
            .add("message", message)
            .apply {
                providerId?.let { add("provider_id", it) }
                conversationId?.let { add("conversation_id", it) }
                imageBase64?.let { add("image_base64", it) }
            }
            .build()
        
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
        
        organizationApiKey?.let {
            requestBuilder.addHeader("X-API-Key", it)
        }
        
        accessToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        } ?: run {
        }
        
        val request = requestBuilder.build()
        
        try {
            // okhttp-sse를 사용한 실시간 스트림 처리
            val sseClient = EventSources.createFactory(client)
            val eventSource = sseClient.newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                }
                
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val event = parseSSEEvent(data)
                        trySend(event)
                    } catch (e: Exception) {
                        // JSON 파싱 에러는 조용히 넘어감
                        trySend(ChatSSEEvent.Error("Parse error: ${e.message}"))
                    }
                }
                
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val errorMessage = when {
                        t?.message?.contains("Connection refused") == true -> "서버가 응답하지 않습니다. 서버 상태를 확인해주세요."
                        t?.message?.contains("timeout") == true -> "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
                        t?.message?.contains("Network is unreachable") == true -> "인터넷 연결을 확인해주세요."
                        response?.code == 401 -> "인증이 필요합니다. 다시 로그인해주세요."
                        response?.code == 403 -> "접근 권한이 없습니다."
                        response?.code == 404 -> "서비스를 찾을 수 없습니다."
                        response?.code == 500 -> "서버 내부 오류가 발생했습니다."
                        else -> "연결 실패: ${t?.message ?: "알 수 없는 오류"}"
                    }
                    trySend(ChatSSEEvent.Error(errorMessage))
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
            val errorMessage = when {
                e.message?.contains("Connection refused") == true -> "서버가 응답하지 않습니다. 서버 상태를 확인해주세요."
                e.message?.contains("timeout") == true -> "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
                e.message?.contains("Network is unreachable") == true -> "인터넷 연결을 확인해주세요."
                e is java.net.UnknownHostException -> "서버를 찾을 수 없습니다. 네트워크 설정을 확인해주세요."
                e is java.net.ConnectException -> "서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요."
                e is java.net.SocketTimeoutException -> "연결 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."
                else -> "요청 오류: ${e.message ?: "알 수 없는 오류"}"
            }
            trySend(ChatSSEEvent.Error(errorMessage))
            close()
        }
        } catch (e: Exception) {
            trySend(ChatSSEEvent.Error("Flow error: ${e.message}"))
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

