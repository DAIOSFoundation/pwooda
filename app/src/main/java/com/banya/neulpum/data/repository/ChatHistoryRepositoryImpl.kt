package com.banya.neulpum.data.repository

import android.content.Context
import com.banya.neulpum.data.remote.ChatHistoryApiService
import com.banya.neulpum.data.remote.ConversationData
import com.banya.neulpum.data.remote.ConversationDetailData
import com.banya.neulpum.data.remote.ConversationDetailResponse
import com.banya.neulpum.data.remote.ConversationResponse
import com.banya.neulpum.data.remote.MessageData
import com.banya.neulpum.domain.entity.ChatMessage
import com.banya.neulpum.domain.entity.Conversation
import com.banya.neulpum.domain.entity.ConversationWithMessages
import com.banya.neulpum.domain.repository.ChatHistoryRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

import com.banya.neulpum.di.AppConfig

class ChatHistoryRepositoryImpl(private val context: Context) : ChatHistoryRepository {
    
    private val baseUrl = AppConfig.BASE_HOST
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val chatHistoryApiService = retrofit.create(ChatHistoryApiService::class.java)
    
    override suspend fun getRecentConversations(apiKey: String, limit: Int): Result<List<Conversation>> {
        return try {
            // SharedPreferences에서 access_token 가져오기
            val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
            val accessToken = prefs.getString("access_token", null)
            val authorization = if (accessToken != null) "Bearer $accessToken" else null
            
            val headerApiKey = apiKey.ifEmpty { null }
            val response = chatHistoryApiService.getConversations(headerApiKey, authorization, limit)
            if (response.isSuccessful) {
                val responseBody = response.body()
                println("ChatHistoryRepository - API Response: $responseBody")
                
                // WrapResponseMiddleware가 {"data": [...]} 형태로 감싸서 보내므로 처리
                val conversationsList = if (responseBody is Map<*, *>) {
                    val data = responseBody["data"] as? List<*>
                    data ?: emptyList()
                } else if (responseBody is List<*>) {
                    responseBody
                } else {
                    emptyList()
                }
                
                val conversations = conversationsList.mapNotNull { convMap ->
                    if (convMap is Map<*, *>) {
                        Conversation(
                            id = convMap["id"] as? String ?: "",
                            title = convMap["title"] as? String ?: "",
                            createdAt = convMap["created_at"] as? String ?: "",
                            updatedAt = convMap["updated_at"] as? String ?: "",
                            messageCount = (convMap["message_count"] as? Number)?.toInt() ?: 0,
                            type = convMap["type"] as? String ?: "chat"
                        )
                    } else null
                }
                println("ChatHistoryRepository - Parsed conversations: ${conversations.size}")
                Result.success(conversations)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getConversationWithMessages(apiKey: String, conversationId: String): Result<ConversationWithMessages> {
        return try {
            // SharedPreferences에서 access_token 가져오기
            val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
            val accessToken = prefs.getString("access_token", null)
            val authorization = if (accessToken != null) "Bearer $accessToken" else null
            
            val response = chatHistoryApiService.getConversation(apiKey, authorization, conversationId)
            if (response.isSuccessful) {
                val responseBody = response.body()
                println("ChatHistoryRepository - getConversation API Response: $responseBody")
                
                // WrapResponseMiddleware가 {"data": {...}} 형태로 감쌀 수 있으므로 처리
                val actualData = if (responseBody is Map<*, *>) {
                    if (responseBody.containsKey("data")) {
                        responseBody["data"] as? Map<String, Any>
                    } else {
                        responseBody
                    }
                } else {
                    responseBody
                }
                
                if (actualData is Map<String, Any>) {
                    val conversation = actualData["conversation"] as? Map<String, Any>
                    val messages = actualData["messages"] as? List<Map<String, Any>>
                    
                    if (conversation != null) {
                        val conversationEntity = Conversation(
                            id = conversation["id"] as? String ?: "",
                            title = conversation["title"] as? String ?: "",
                            createdAt = conversation["created_at"] as? String ?: "",
                            updatedAt = conversation["updated_at"] as? String ?: "",
                            messageCount = conversation["message_count"] as? Int ?: 0
                        )
                        
                        val messageEntities = messages?.map { msgMap ->
                            ChatMessage(
                                id = msgMap["id"] as? String ?: "",
                                content = msgMap["content"] as? String ?: "",
                                isUser = msgMap["role"] as? String == "user",
                                timestamp = System.currentTimeMillis()
                            )
                        } ?: emptyList()
                        
                        println("ChatHistoryRepository - Loaded conversation: ${conversationEntity.title}, messages: ${messageEntities.size}")
                        Result.success(ConversationWithMessages(conversationEntity, messageEntities))
                    } else {
                        Result.failure(Exception("No conversation data in response"))
                    }
                } else {
                    Result.failure(Exception("Invalid response format: $actualData"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
