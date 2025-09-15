package com.banya.neulpum.data.remote

import retrofit2.Response
import retrofit2.http.*

interface ChatHistoryApiService {
    
    @GET("chat/conversations/")
    suspend fun getConversations(
        @Header("X-API-Key") apiKey: String? = null,
        @Header("Authorization") authorization: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): Response<Map<String, Any>>
    
    @GET("chat/conversations/{conversationId}/")
    suspend fun getConversation(
        @Header("X-API-Key") apiKey: String? = null,
        @Header("Authorization") authorization: String? = null,
        @Path("conversationId") conversationId: String
    ): Response<Map<String, Any>>
}

data class ConversationResponse(
    val success: Boolean,
    val data: List<ConversationData>? = null,
    val error: String? = null
)

data class ConversationData(
    val id: String,
    val title: String,
    val created_at: String,
    val updated_at: String,
    val message_count: Int,
    val type: String = "chat"
)

data class ConversationDetailResponse(
    val success: Boolean,
    val data: ConversationDetailData? = null,
    val error: String? = null
)

data class ConversationDetailData(
    val conversation: ConversationData,
    val messages: List<MessageData>
)

data class MessageData(
    val id: String,
    val role: String,
    val content: String,
    val created_at: String
)
