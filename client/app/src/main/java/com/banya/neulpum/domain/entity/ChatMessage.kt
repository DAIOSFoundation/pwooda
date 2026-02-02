package com.banya.neulpum.domain.entity

import android.graphics.Bitmap
import java.util.*

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val toolName: String? = null,
    val toolResult: String? = null,
    val image: Bitmap? = null,
    val isTyping: Boolean = false,
    val visibleText: String = content
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentStreamingMessage: String = "",
    val currentTool: String? = null
)


