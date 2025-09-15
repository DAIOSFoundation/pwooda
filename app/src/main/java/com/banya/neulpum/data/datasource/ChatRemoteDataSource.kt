package com.banya.neulpum.data.datasource

import com.banya.neulpum.data.remote.ChatSSEEvent
import com.banya.neulpum.data.remote.SSEChatService
import kotlinx.coroutines.flow.Flow

class ChatRemoteDataSource() {
    private val sseService = SSEChatService()

    fun stream(
        message: String,
        organizationApiKey: String?,
        providerId: String? = null,
        conversationId: String? = null,
        imageBase64: String? = null,
        accessToken: String? = null
    ): Flow<ChatSSEEvent> = sseService.chatSSE(
        message = message,
        organizationApiKey = organizationApiKey,
        accessToken = accessToken,
        conversationId = conversationId,
        imageBase64 = imageBase64,
        providerId = providerId
    )
}


