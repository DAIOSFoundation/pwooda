package com.banya.neulpum.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banya.neulpum.domain.entity.ChatMessage
import com.banya.neulpum.domain.entity.ChatState
import com.banya.neulpum.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    fun sendMessage(message: String, organizationApiKey: String? = null) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            _chatState.value = _chatState.value.copy(isLoading = true, error = null)
            
            try {
                chatRepository.sendMessage(
                    message = message,
                    organizationApiKey = organizationApiKey
                ).collect { chatMessage ->
                    val currentMessages = _chatState.value.messages.toMutableList()
                    
                    // 기존 메시지 업데이트 또는 새 메시지 추가
                    val existingIndex = currentMessages.indexOfFirst { it.id == chatMessage.id }
                    if (existingIndex != -1) {
                        currentMessages[existingIndex] = chatMessage
                    } else {
                        currentMessages.add(chatMessage)
                    }
                    
                    _chatState.value = _chatState.value.copy(
                        messages = currentMessages,
                        isLoading = chatMessage.isStreaming,
                        currentStreamingMessage = if (chatMessage.isStreaming) chatMessage.content else "",
                        currentTool = chatMessage.toolName
                    )
                }
            } catch (e: Exception) {
                _chatState.value = _chatState.value.copy(
                    isLoading = false,
                    error = e.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }
        }
    }
    
    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearChat()
            _chatState.value = ChatState()
        }
    }
    
    fun clearError() {
        _chatState.value = _chatState.value.copy(error = null)
    }
}
