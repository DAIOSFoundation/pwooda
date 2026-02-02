package com.banya.neulpum.domain.repository

import com.banya.neulpum.domain.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun sendMessage(
        message: String,
        organizationApiKey: String? = null,
        providerId: String? = null
    ): Flow<ChatMessage>
    
    suspend fun clearChat(): Boolean
    suspend fun getChatHistory(): List<ChatMessage>
}
