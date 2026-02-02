package com.banya.neulpum.domain.entity

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val messageCount: Int,
    val type: String = "chat" // "chat" or "voice"
)

data class ConversationWithMessages(
    val conversation: Conversation,
    val messages: List<ChatMessage>
)


