package com.banya.neulpum.domain.repository

import com.banya.neulpum.domain.entity.Conversation
import com.banya.neulpum.domain.entity.ConversationWithMessages

interface ChatHistoryRepository {
    suspend fun getRecentConversations(apiKey: String, limit: Int = 10): Result<List<Conversation>>
    suspend fun getConversationWithMessages(apiKey: String, conversationId: String): Result<ConversationWithMessages>
}


