package com.banya.neulpum.data.repository

import com.banya.neulpum.data.datasource.ChatRemoteDataSource
import com.banya.neulpum.data.remote.ChatSSEEvent
import com.banya.neulpum.domain.entity.ChatMessage
import com.banya.neulpum.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

class ChatRepositoryImpl : ChatRepository {
    private val chatHistory = mutableListOf<ChatMessage>()
    private val remote = ChatRemoteDataSource()

    override suspend fun sendMessage(
        message: String,
        organizationApiKey: String?,
        providerId: String?
    ): Flow<ChatMessage> = flow {
        // 사용자 메시지 추가
        val userMessage = ChatMessage(
            content = message,
            isUser = true
        )
        chatHistory.add(userMessage)
        emit(userMessage)
        
        // AI 응답을 위한 빈 메시지 생성
        val aiMessageId = UUID.randomUUID().toString()
        val aiMessage = ChatMessage(
            id = aiMessageId,
            content = "",
            isUser = false,
            isStreaming = true
        )
        chatHistory.add(aiMessage)
        emit(aiMessage)
        
        // Gemini 스트림 처리
        try {
            remote.stream(
                message = message,
                organizationApiKey = organizationApiKey,
                providerId = providerId,
                chatHistory = chatHistory.filter { !it.isStreaming } // 스트리밍 중인 메시지는 제외
            ).collect { event ->
                when (event) {
                    is ChatSSEEvent.Step -> {
                        // 도구 실행 중
                        if (event.tool != null) {
                            val toolMessage = ChatMessage(
                                content = "🔧 ${event.tool} 실행 중...",
                                isUser = false,
                                isStreaming = true,
                                toolName = event.tool
                            )
                            chatHistory.add(toolMessage)
                            emit(toolMessage)
                        }
                        
                        // 도구 결과
                        if (event.result != null) {
                            val resultMessage = ChatMessage(
                                content = "✅ ${event.tool ?: "도구"} 결과: ${event.result}",
                                isUser = false,
                                isStreaming = true,
                                toolName = event.tool,
                                toolResult = event.result
                            )
                            chatHistory.add(resultMessage)
                            emit(resultMessage)
                        }
                    }
                    is ChatSSEEvent.Final -> {
                        // 최종 응답
                        val finalMessage = ChatMessage(
                            id = aiMessageId,
                            content = event.result,
                            isUser = false,
                            isStreaming = false
                        )
                        // 기존 스트리밍 메시지 업데이트
                        val index = chatHistory.indexOfFirst { it.id == aiMessageId }
                        if (index != -1) {
                            chatHistory[index] = finalMessage
                        }
                        emit(finalMessage)
                    }
                    is ChatSSEEvent.Error -> {
                        // 에러 메시지
                        val errorMessage = ChatMessage(
                            content = "❌ 오류: ${event.message}",
                            isUser = false,
                            isStreaming = false
                        )
                        chatHistory.add(errorMessage)
                        emit(errorMessage)
                    }
                    is ChatSSEEvent.Unknown -> {
                        // 알 수 없는 이벤트 (무시)
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = ChatMessage(
                content = "❌ 연결 오류: ${e.message}",
                isUser = false,
                isStreaming = false
            )
            chatHistory.add(errorMessage)
            emit(errorMessage)
        }
    }
    
    override suspend fun clearChat(): Boolean {
        return try {
            chatHistory.clear()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getChatHistory(): List<ChatMessage> {
        return chatHistory.toList()
    }
}
