package com.banya.neulpum.data.datasource

import android.graphics.Bitmap
import android.util.Base64
import com.banya.neulpum.data.remote.ChatSSEEvent
import com.banya.neulpum.data.remote.GeminiChatService
import com.banya.neulpum.domain.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

class ChatRemoteDataSource() {
    private val geminiService = GeminiChatService()

    fun stream(
        message: String,
        organizationApiKey: String?,
        providerId: String? = null,
        conversationId: String? = null,
        imageBase64: String? = null,
        accessToken: String? = null,
        chatHistory: List<ChatMessage> = emptyList()
    ): Flow<ChatSSEEvent> {
        // Base64 이미지를 Bitmap으로 변환
        val imageBitmap = imageBase64?.let { base64 ->
            try {
                val imageBytes = Base64.decode(base64, Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                null
            }
        }
        
        // 클라이언트에서 직접 Gemini API 호출
        return geminiService.chatStream(
            message = message,
            image = imageBitmap,
            chatHistory = chatHistory
        )
    }
}


