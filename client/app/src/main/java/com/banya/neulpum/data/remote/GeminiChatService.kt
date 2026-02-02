package com.banya.neulpum.data.remote

import android.graphics.Bitmap
import com.banya.neulpum.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class GeminiChatService {
    
    private val tavilyService = TavilyWebSearchService()
    
    private fun getGeminiApiKey(): String {
        // BuildConfig에서 API 키 가져오기
        // local.properties에서 GEMINI_API_KEY를 읽어서 BuildConfig에 설정됨
        return BuildConfig.GEMINI_API_KEY.ifEmpty { 
            throw IllegalStateException("Gemini API 키가 설정되지 않았습니다. local.properties 파일에 GEMINI_API_KEY를 추가하세요.")
        }
    }
    
    /**
     * 메시지에서 웹 검색이 필요한지 판단
     */
    private fun needsWebSearch(message: String): Boolean {
        val searchKeywords = listOf(
            "최신", "현재", "오늘", "어제", "뉴스", "검색", "찾아", "알려줘", "알아봐줘",
            "트위터", "인스타그램", "페이스북", "구글",
            "what", "when", "where", "who", "how", "latest", "current", "news", "search",
            "twitter", "instagram", "facebook", "google"
        )
        return searchKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 메시지에서 검색 쿼리 추출
     */
    private fun extractSearchQuery(message: String): String {
        // 간단한 추출 로직: 메시지 전체를 검색 쿼리로 사용
        // 필요시 더 정교한 추출 로직 구현 가능
        return message.trim()
    }
    
    /**
     * 현재 시간을 한국 시간대로 포맷팅
     */
    private fun getCurrentDateTime(): String {
        val calendar = Calendar.getInstance()
        val timeZone = TimeZone.getTimeZone("Asia/Seoul")
        calendar.timeZone = timeZone
        
        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일 HH시 mm분", Locale.KOREAN)
        dateFormat.timeZone = timeZone
        
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "일요일"
            Calendar.MONDAY -> "월요일"
            Calendar.TUESDAY -> "화요일"
            Calendar.WEDNESDAY -> "수요일"
            Calendar.THURSDAY -> "목요일"
            Calendar.FRIDAY -> "금요일"
            Calendar.SATURDAY -> "토요일"
            else -> ""
        }
        
        return "${dateFormat.format(calendar.time)} ($dayOfWeek)"
    }
    
    /**
     * 위치 정보 가져오기
     */
    private fun getCurrentLocation(): String {
        return "서울 강남"
    }
    
    /**
     * Gemini Flash Latest 모델을 사용하여 채팅 메시지 전송
     * @param message 사용자 메시지
     * @param image 이미지 (선택사항)
     * @param chatHistory 이전 대화 기록 (선택사항)
     */
    fun chatStream(
        message: String,
        image: Bitmap? = null,
        chatHistory: List<com.banya.neulpum.domain.entity.ChatMessage> = emptyList()
    ): Flow<ChatSSEEvent> = flow {
        try {
            val apiKey = getGeminiApiKey()
            
            // 웹 검색이 필요한지 확인
            var webSearchResults: String? = null
            if (needsWebSearch(message)) {
                try {
                    emit(ChatSSEEvent.Step(
                        stage = "searching",
                        detail = "웹 검색 중...",
                        tool = "web_search",
                        result = null
                    ))
                    
                    val searchQuery = extractSearchQuery(message)
                    val searchResponse = tavilyService.search(searchQuery, maxResults = 5)
                    webSearchResults = tavilyService.formatSearchResults(searchResponse)
                    
                    emit(ChatSSEEvent.Step(
                        stage = "search_complete",
                        detail = "웹 검색 완료",
                        tool = "web_search",
                        result = "${searchResponse.results.size}개의 결과를 찾았습니다."
                    ))
                } catch (e: Exception) {
                    // 웹 검색 실패해도 계속 진행
                    webSearchResults = "웹 검색 중 오류가 발생했습니다: ${e.message}"
                }
            }
            
            // Gemini 2.5 Flash 모델 생성
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )
            
            // 현재 시간과 위치 정보 가져오기
            val currentDateTime = getCurrentDateTime()
            val currentLocation = getCurrentLocation()
            
            // System prompt와 웹 검색 결과를 포함한 사용자 메시지 생성
            val userMessage = buildString {
                // System instruction
                append("당신은 도움이 되는 AI 어시스턴트입니다. ")
                append("사용자의 질문에 정확하고 도움이 되는 답변을 제공해주세요. ")
                append("답변은 한국어로 작성해주세요.\n\n")
                
                // 현재 위치와 시간 정보
                append("=== 현재 상황 ===\n")
                append("위치: $currentLocation\n")
                append("현재 시간: $currentDateTime\n\n")
                
                // 웹 검색 결과가 있으면 포함
                if (webSearchResults != null) {
                    append("=== 웹 검색 결과 ===\n")
                    append(webSearchResults)
                    append("\n위 검색 결과를 참고하여 사용자의 질문에 답변해주세요. ")
                    append("검색 결과의 출처와 URL도 함께 언급해주세요.\n\n")
                }
                
                // 이전 대화 기록 추가
                if (chatHistory.isNotEmpty()) {
                    append("=== 이전 대화 ===\n")
                    chatHistory.takeLast(5).forEach { chatMessage ->
                        if (chatMessage.isUser) {
                            append("사용자: ${chatMessage.content}\n")
                        } else {
                            append("AI: ${chatMessage.content}\n")
                        }
                    }
                    append("\n")
                }
                
                // 사용자 메시지
                append("사용자 질문: $message")
            }
            
            // 스트리밍 응답 받기 (간단한 String 방식 사용)
            val response = model.generateContentStream(userMessage)
            
            var fullResponse = ""
            var isFirstChunk = true
            
            response.collect { chunk ->
                val text = chunk.text
                if (text != null) {
                    fullResponse += text
                    // 첫 번째 청크에서만 Step 이벤트 전송
                    if (isFirstChunk) {
                        emit(ChatSSEEvent.Step(
                            stage = "generating",
                            detail = "응답 생성 중...",
                            tool = null,
                            result = null
                        ))
                        isFirstChunk = false
                    }
                    // 스트리밍 중간 결과를 Final 이벤트로 전송 (점진적 업데이트)
                    emit(ChatSSEEvent.Final(
                        result = fullResponse,
                        conversationId = null
                    ))
                }
            }
            
            // 최종 응답 전송 (이미 위에서 전송되었지만 명확성을 위해)
            if (fullResponse.isNotEmpty()) {
                emit(ChatSSEEvent.Final(
                    result = fullResponse,
                    conversationId = null
                ))
            }
            
        } catch (e: Exception) {
            emit(ChatSSEEvent.Error(
                message = when {
                    e.message?.contains("API key") == true -> "Gemini API 키가 유효하지 않습니다."
                    e.message?.contains("quota") == true -> "API 사용량 한도를 초과했습니다."
                    e.message?.contains("network") == true -> "네트워크 연결을 확인해주세요."
                    else -> "오류 발생: ${e.message ?: "알 수 없는 오류"}"
                }
            ))
        }
    }
    
    /**
     * Bitmap을 ByteArray로 변환
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        // 이미지 크기 최적화 (최대 1024x1024)
        val maxSize = 1024
        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return outputStream.toByteArray()
    }
}

