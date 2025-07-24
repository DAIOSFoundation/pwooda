package com.banya.pwooda.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banya.pwooda.MainActivity
import com.banya.pwooda.service.GoogleCloudTTSService
import com.banya.pwooda.service.PaymentService
import com.banya.pwooda.service.ProductDataService
import com.banya.pwooda.service.CustomerDataService
import com.banya.pwooda.service.FalAIService
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.banya.pwooda.data.*

data class ChatMessage(
    val role: String, // "user" 또는 "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GeminiState(
    val isLoading: Boolean = false,
    val response: String = "",
    val error: String = "",
    val isSpeaking: Boolean = false,
    val isVoiceDownloading: Boolean = false, // 추가
    val isListening: Boolean = false, // 음성 인식 중 상태 추가
    val isCameraActive: Boolean = false, // 카메라 촬영 중 상태 추가
    val hasTTSError: Boolean = false, // TTS 오류 발생 여부 추가
    val showPaymentButton: Boolean = false,
    val showQRCode: Boolean = false,
    val qrCodeBitmap: android.graphics.Bitmap? = null,
    val currentProduct: com.banya.pwooda.data.Product? = null,
    val showProductImage: Boolean = false,
    val productImageResourceName: String? = null,
    val relatedNewProduct: com.banya.pwooda.data.Product? = null,
    val showNewProductImage: Boolean = false,
    val newProductImageResourceName: String? = null,
    val chatHistory: List<ChatMessage> = emptyList(),
    val shouldShowChatBubble: Boolean = false, // 챗 버블 표시 여부
    val isGeneratingImage: Boolean = false, // 이미지 생성 중 상태
    val generatedImage: android.graphics.Bitmap? = null, // 생성된 이미지
    val imageGenerationProgress: String = "", // 이미지 생성 진행 상태
    val shouldShowGeneratedImage: Boolean = false // 생성된 이미지 표시 여부
)

class GeminiViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(GeminiState())
    val state: StateFlow<GeminiState> = _state.asStateFlow()

    private var generativeModel: GenerativeModel? = null
    private var ttsService: GoogleCloudTTSService? = null
    private val paymentService = PaymentService()
    private var productDataService: ProductDataService? = null
    private var customerDataService: CustomerDataService? = null
    private val falAIService = FalAIService()
    val customerDataServicePublic: CustomerDataService?
        get() = customerDataService
    private var currentUser: User? = null
    private var currentSchedule: WeeklySchedule? = null
    private var educatorMaterials: EducatorMaterials? = null

    // MainActivity 참조 (환영 메시지 TTS 중지용)
    private var mainActivity: MainActivity? = null

    // 현재 인식된 고객 이름 (얼굴 감지 시 설정)
    private val _recognizedCustomerName = MutableStateFlow<String?>(null)
    val recognizedCustomerName: StateFlow<String?> = _recognizedCustomerName.asStateFlow()

    // 인식된 고객 ID (항상 유지)
    private var recognizedCustomerId: String? = null

    // 대화 히스토리 관리
    private val chatHistory = mutableListOf<ChatMessage>()

    // 대화 히스토리 초기화
    fun clearChatHistory() {
        chatHistory.clear()
        _state.value = _state.value.copy(chatHistory = emptyList())
        android.util.Log.d("GeminiViewModel", "대화 히스토리 초기화됨")
    }

    // 대화 히스토리 가져오기
    fun getChatHistory(): List<ChatMessage> = chatHistory.toList()

    // 최근 사용자 질문 10개 가져오기
    fun getRecentUserQuestions(): List<String> {
        return chatHistory
            .filter { it.role == "user" }
            .takeLast(10)
            .map { it.content }
    }

    // 대화 히스토리에서 특정 키워드 검색
    fun searchChatHistory(keyword: String): List<ChatMessage> {
        return chatHistory.filter { message ->
            message.content.contains(keyword, ignoreCase = true)
        }
    }

    // 대화 히스토리 요약 정보 가져오기
    fun getChatSummary(): String {
        if (chatHistory.isEmpty()) return "대화 기록이 없습니다."

        val userMessages = chatHistory.filter { it.role == "user" }
        val assistantMessages = chatHistory.filter { it.role == "assistant" }
        
        return """
        대화 요약:
        - 총 대화 수: ${chatHistory.size}개
        - 사용자 질문: ${userMessages.size}개
        - AI 응답: ${assistantMessages.size}개
        - 최근 질문: ${userMessages.takeLast(3).joinToString(", ") { it.content.take(20) + "..." }}
        """.trimIndent()
    }

    // 대화 히스토리에서 중요한 정보 추출
    fun extractImportantInfo(): String {
        val importantKeywords = listOf("이름", "나이", "목표", "일정", "약", "약물", "선호", "싫어", "좋아", "문제", "고민")
        val relevantMessages = chatHistory.filter { message ->
            importantKeywords.any { keyword ->
                message.content.contains(keyword, ignoreCase = true)
            }
        }
        
        return if (relevantMessages.isNotEmpty()) {
            "중요 정보: ${relevantMessages.takeLast(5).joinToString(" | ") { it.content.take(30) + "..." }}"
        } else {
            "중요 정보 없음"
        }
    }

    // 대화 히스토리 크기 제한 (메모리 효율성을 위해)
    private fun limitChatHistory() {
        if (chatHistory.size > 50) { // 최대 50개 메시지 유지 (증가)
            val toRemove = chatHistory.size - 50
            repeat(toRemove) {
                chatHistory.removeAt(0) // 가장 오래된 메시지부터 제거
            }
            _state.value = _state.value.copy(chatHistory = chatHistory.toList())
            android.util.Log.d("GeminiViewModel", "대화 히스토리 크기 제한: ${toRemove}개 메시지 제거됨")
        }
    }

    // 대화 히스토리를 문자열로 구성
    private fun buildConversationHistory(): String {
        if (chatHistory.isEmpty()) return ""

        // 전체 대화 히스토리를 시간순으로 구성 (최대 20개 메시지)
        val recentMessages = chatHistory.takeLast(20)
        
        val conversationHistory = recentMessages.mapIndexed { index, message ->
            when (message.role) {
                "user" -> "사용자: ${message.content}"
                "assistant" -> "AI: ${message.content}"
                else -> ""
            }
        }.filter { it.isNotEmpty() }

        if (conversationHistory.isEmpty()) return ""

        return """
        이전 대화 내용:
        ${conversationHistory.joinToString("\n")}
        
        위의 대화 내용을 참고하여 다음 질문에 답변해주세요. 이전 대화에서 언급된 내용이나 맥락을 고려하여 일관성 있게 답변해주세요.
        """.trimIndent()
    }

    // 질문에서 그림 그리기 프롬프트 추출 (한글 그대로 전달)
    private fun extractDrawingPrompt(question: String): String {
        // "그림 그려줘", "그려줘" 등의 키워드 제거하고 실제 내용만 추출
        val drawingKeywords = listOf(
            "그림 그려줘", "그려줘", "이미지 만들어줘", "사진 그려줘", 
            "그림 그려달라고", "그려달라고", "그림 그려주세요", "그려주세요"
        )
        
        var prompt = question
        for (keyword in drawingKeywords) {
            prompt = prompt.replace(keyword, "").trim()
        }
        
        // 추가 정리
        prompt = prompt.replace("에 대해", "")
            .replace("에 대해서", "")
            .replace("을", "")
            .replace("를", "")
            .trim()
        
        // 프롬프트가 비어있으면 기본값 설정
        if (prompt.isEmpty()) {
            prompt = "아름다운 풍경"
        }
        
        android.util.Log.d("GeminiViewModel", "추출된 그림 프롬프트: $prompt")
        return prompt
    }

    // Gemini를 통해 사용자의 음성 입력 요청을 영문 이미지 생성 프롬프트로 변환
    private suspend fun translateToImagePrompt(userRequest: String): String {
        try {
            val translationPrompt = """
                사용자가 그림 그리기를 요청했습니다. 다음 요청을 영문 이미지 생성 프롬프트로 변환해줘.
                
                요구사항:
                1. 사용자의 요청을 영어로 번역하고, 이미지 생성에 적합한 키워드로 변환
                2. 배경 없는 이미지로 생성되도록 "transparent background, no background, isolated" 추가
                3. 지브리 스타일로 생성되도록 "Studio Ghibli style, Hayao Miyazaki, anime, watercolor, soft lighting, magical atmosphere" 추가
                4. 고품질 이미지로 생성되도록 "detailed, high quality, masterpiece" 추가
                5. 영어로만 응답하고, 다른 설명은 하지 마
                
                사용자 요청: $userRequest
                
                예시:
                - "귀여운 강아지 그려줘" → "cute dog, Studio Ghibli style, Hayao Miyazaki, anime, watercolor, soft lighting, magical atmosphere, transparent background, no background, isolated, detailed, high quality, masterpiece"
                - "예쁜 꽃 그려줘" → "beautiful flower, Studio Ghibli style, Hayao Miyazaki, anime, watercolor, soft lighting, magical atmosphere, transparent background, no background, isolated, detailed, high quality, masterpiece"
            """.trimIndent()
            
            // 간단한 Gemini API 호출
            val content = content {
                text(translationPrompt)
            }
            
            val response = generativeModel?.generateContent(content)
            val responseText = response?.text ?: ""
            
            android.util.Log.d("GeminiViewModel", "Gemini 프롬프트 변환 결과: $responseText")
            return responseText.ifEmpty { 
                // 기본 영문 프롬프트로 폴백
                "cute dog, Studio Ghibli style, Hayao Miyazaki, anime, watercolor, soft lighting, magical atmosphere, transparent background, no background, isolated, detailed, high quality, masterpiece"
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "프롬프트 변환 실패", e)
            // 기본 영문 프롬프트로 폴백
            return "cute dog, Studio Ghibli style, Hayao Miyazaki, anime, watercolor, soft lighting, magical atmosphere, transparent background, no background, isolated, detailed, high quality, masterpiece"
        }
    }

    // 그림 그리기 요청 처리
    private suspend fun handleDrawingRequest(question: String, personalizedContext: String) {
        try {
            android.util.Log.d("GeminiViewModel", "그림 그리기 요청 처리 시작")
            
            // 사용자 메시지를 대화 히스토리에 추가
            val userMessage = ChatMessage("user", question)
            chatHistory.add(userMessage)
            limitChatHistory() // 대화 히스토리 크기 제한
            
            // Gemini를 통해 사용자의 원본 요청을 영문 프롬프트로 변환
            val englishPrompt = translateToImagePrompt(question)
            android.util.Log.d("GeminiViewModel", "변환된 영문 프롬프트: $englishPrompt")
            
            // 초기 응답 설정
            val initialResponse = if (currentUser != null) {
                "${currentUser?.nickname}야! 그림을 그려줄게! 잠깐만 기다려."
            } else {
                "그림을 그려줄게! 잠깐만 기다려."
            }
            
            // AI 응답을 대화 히스토리에 추가
            val assistantMessage = ChatMessage("assistant", initialResponse)
            chatHistory.add(assistantMessage)
            
            _state.value = _state.value.copy(
                isLoading = false,
                response = initialResponse,
                chatHistory = chatHistory.toList(),
                shouldShowChatBubble = true,
                isGeneratingImage = true,
                imageGenerationProgress = "그림 생성 중..."
            )
            
            // ComfyUI 이미지 생성 API 호출
            android.util.Log.d("GeminiViewModel", "ComfyUI 이미지 생성 API 호출 시작")
            val imageData = falAIService.generateImage(englishPrompt)
            
            if (imageData != null) {
                android.util.Log.d("GeminiViewModel", "이미지 데이터 받음")
                
                // 이미지 다운로드 및 처리
                val bitmap = falAIService.downloadImage(imageData)
                
                if (bitmap != null) {
                    val finalResponse = if (currentUser != null) {
                        "${currentUser?.nickname}야! 그림이 완성됐어! 어떠니?"
                    } else {
                        "그림이 완성됐어! 어떠니?"
                    }
                    
                    // 최종 응답으로 업데이트
                    val finalAssistantMessage = ChatMessage("assistant", finalResponse)
                    chatHistory[chatHistory.size - 1] = finalAssistantMessage
                    
                    _state.value = _state.value.copy(
                        response = finalResponse,
                        chatHistory = chatHistory.toList(),
                        isGeneratingImage = false,
                        generatedImage = bitmap,
                        imageGenerationProgress = "",
                        shouldShowGeneratedImage = true // 이미지 표시 활성화
                    )
                    
                    // TTS로 응답 읽기
                    speakText(finalResponse)
                } else {
                    android.util.Log.e("GeminiViewModel", "이미지 처리 실패")
                    val errorResponse = "그림을 처리하는데 실패했어. 다시 시도해볼까?"
                    val errorAssistantMessage = ChatMessage("assistant", errorResponse)
                    chatHistory[chatHistory.size - 1] = errorAssistantMessage
                    
                    _state.value = _state.value.copy(
                        response = errorResponse,
                        chatHistory = chatHistory.toList(),
                        isGeneratingImage = false,
                        imageGenerationProgress = ""
                    )
                }
            } else {
                android.util.Log.e("GeminiViewModel", "ComfyUI 이미지 생성 API 호출 실패")
                val errorResponse = "그림 생성에 실패했어. 다시 시도해볼까?"
                val errorAssistantMessage = ChatMessage("assistant", errorResponse)
                chatHistory[chatHistory.size - 1] = errorAssistantMessage
                
                _state.value = _state.value.copy(
                    response = errorResponse,
                    chatHistory = chatHistory.toList(),
                    isGeneratingImage = false,
                    imageGenerationProgress = ""
                )
            }
            
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "그림 그리기 처리 중 오류 발생", e)
            val errorResponse = "그림 그리기 중 오류가 발생했어. 다시 시도해볼까?"
            val errorAssistantMessage = ChatMessage("assistant", errorResponse)
            chatHistory[chatHistory.size - 1] = errorAssistantMessage
            
            _state.value = _state.value.copy(
                response = errorResponse,
                chatHistory = chatHistory.toList(),
                isGeneratingImage = false,
                imageGenerationProgress = ""
            )
        }
    }

    // 그림 저장 요청 처리
    private suspend fun handleImageSaveRequest(question: String, personalizedContext: String) {
        try {
            android.util.Log.d("GeminiViewModel", "그림 저장 요청 처리 시작")
            
            // 사용자 메시지를 대화 히스토리에 추가
            val userMessage = ChatMessage("user", question)
            chatHistory.add(userMessage)
            limitChatHistory()
            
            // 현재 생성된 이미지가 있는지 확인
            val currentImage = _state.value.generatedImage
            if (currentImage == null) {
                val errorResponse = "저장할 그림이 없어요. 먼저 그림을 그려주세요."
                val errorAssistantMessage = ChatMessage("assistant", errorResponse)
                chatHistory.add(errorAssistantMessage)
                
                _state.value = _state.value.copy(
                    response = errorResponse,
                    chatHistory = chatHistory.toList(),
                    shouldShowChatBubble = true
                )
                
                speakText(errorResponse)
                return
            }
            
            // 이미지를 갤러리에 저장
            val saveResult = saveImageToGallery(currentImage)
            
            val response = if (saveResult) {
                if (currentUser != null) {
                    "${currentUser?.nickname}야! 그림을 갤러리에 저장했어요!"
                } else {
                    "그림을 갤러리에 저장했어요!"
                }
            } else {
                "그림 저장에 실패했어요. 다시 시도해볼까요?"
            }
            
            // AI 응답을 대화 히스토리에 추가
            val assistantMessage = ChatMessage("assistant", response)
            chatHistory.add(assistantMessage)
            
            _state.value = _state.value.copy(
                response = response,
                chatHistory = chatHistory.toList(),
                shouldShowChatBubble = true
            )
            
            // TTS로 응답 읽기
            speakText(response)
            
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "그림 저장 처리 중 오류 발생", e)
            val errorResponse = "그림 저장 중 오류가 발생했어요. 다시 시도해볼까요?"
            val errorAssistantMessage = ChatMessage("assistant", errorResponse)
            chatHistory.add(errorAssistantMessage)
            
            _state.value = _state.value.copy(
                response = errorResponse,
                chatHistory = chatHistory.toList(),
                shouldShowChatBubble = true
            )
            
            speakText(errorResponse)
        }
    }

    // 이미지를 갤러리에 저장하는 함수
    private fun saveImageToGallery(bitmap: Bitmap): Boolean {
        return try {
            android.util.Log.d("GeminiViewModel", "이미지 갤러리 저장 시작")
            
            // MediaStore를 사용하여 이미지 저장
            val filename = "Pwooda_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Pwooda")
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                android.util.Log.d("GeminiViewModel", "이미지 갤러리 저장 성공: $uri")
                true
            } else {
                android.util.Log.e("GeminiViewModel", "이미지 갤러리 저장 실패: URI 생성 실패")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "이미지 갤러리 저장 중 오류", e)
            false
        }
    }
    private fun cleanResponseText(text: String): String {
        return text
            .trim() // 앞뒤 공백 제거
            .replace("*", "") // * 문자 제거
            .replace("~", ".") // ~ 문자를 .으로 변경
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // 제어 문자 제거 (ASCII 0-31, 127)
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "") // 제로 너비 공백, 결합 문자 제거
            .replace(Regex("[\\uFFFD]"), "") // 대체 문자 제거
            .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), "") // 추가 제어 문자 제거
            .replace(Regex("[\\uD83D\\uDE00-\\uD83D\\uDE4F]"), "") // 이모티콘 (😀-😏)
            .replace(Regex("[\\uD83C\\uDF00-\\uD83C\\uDFFF]"), "") // 기타 기호 및 픽토그램
            .replace(Regex("[\\uD83D\\uDE80-\\uD83D\\uDEFF]"), "") // 교통 및 지도 기호
            .replace(Regex("[\\uD83C\\uDDE6-\\uD83C\\uDDFF]"), "") // 국기
            .replace(Regex("[\\u2600-\\u26FF]"), "") // 기타 기호
            .replace(Regex("[\\u2700-\\u27BF]"), "") // 장식 기호
            .replace(Regex("[\\uFE00-\\uFE0F]"), "") // 변형 선택자
            .replace(Regex("[\\uD83E\\uDD00-\\uD83E\\uDDFF]"), "") // 보충 기호 및 픽토그램
            .replace(Regex("[\\uD83C\\uDC00-\\uD83C\\uDC2F]"), "") // 마작 타일
            .replace(Regex("[\\uD83C\\uDC30-\\uD83C\\uDC9F]"), "") // 도미노 타일
            .replace(Regex("[\\uD83C\\uDCA0-\\uD83C\\uDCFF]"), "") // 플레잉 카드
            .replace(Regex("[\\uD83C\\uDD00-\\uD83D\\uDE4F]"), "") // 문자 기호
            .replace(Regex("[\\uD83E\\uDE50-\\uD83E\\uDE7F]"), "") // 장식 기호
            .replace(Regex("[\\uD83D\\uDE80-\\uD83D\\uDEFF]"), "") // 교통 및 지도 기호
            .replace(Regex("[\\uD83E\\uDF80-\\uD83E\\uDFFF]"), "") // 기하학적 도형
            .replace(Regex("[\\uD83E\\uDC00-\\uD83E\\uDCFF]"), "") // 보충 화살표
            .replace(Regex("[\\uD83E\\uDD00-\\uD83E\\uDDFF]"), "") // 보충 기호 및 픽토그램
            .replace(Regex("[\\uD83E\\uDE80-\\uD83E\\uDEAF]"), "") // 체스 기호
            .replace(Regex("[\\uD83E\\uDEB0-\\uD83E\\uDEBF]"), "") // 기호 및 픽토그램 확장
            .replace(Regex("[\\uD83E\\uDEC0-\\uD83E\\uDEFF]"), "") // 기호 및 픽토그램 확장
            .replace(Regex("[\\uD83E\\uDED0-\\uD83E\\uDEFF]"), "") // 기호 및 픽토그램 확장
            .replace(Regex("[\\uD83E\\uDEE0-\\uD83E\\uDEFF]"), "") // 기호 및 픽토그램 확장
            .replace(Regex("[\\uD83E\\uDEF0-\\uD83E\\uDEFF]"), "") // 기호 및 픽토그램 확장
            .replace(Regex("\\s+"), " ") // 연속된 공백을 하나로
            .trim() // 다시 앞뒤 공백 제거
    }

    fun initializeGemini(apiKey: String) {
        generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey
        )
    }

    fun initializeTTS(context: Context) {
        android.util.Log.d("TTS", "initializeTTS 호출됨")
        ttsService = GoogleCloudTTSService(context)
        productDataService = ProductDataService(context)
        customerDataService = CustomerDataService(context)
        educatorMaterials = customerDataService?.getEducatorMaterials()
        android.util.Log.d("TTS", "TTS 서비스 초기화 완료: ttsService=${ttsService != null}, productDataService=${productDataService != null}, customerDataService=${customerDataService != null}")
    }

    // MainActivity 참조 설정
    fun setMainActivity(activity: MainActivity) {
        mainActivity = activity
        android.util.Log.d("GeminiViewModel", "MainActivity 참조 설정됨")
    }

    // 인식된 고객 이름 설정 (MainActivity에서 호출)
    fun setRecognizedCustomer(name: String?) {
        _recognizedCustomerName.value = name
        android.util.Log.d("GeminiViewModel", "인식된 고객 이름 설정됨: $name")
    }

    // 인식된 고객 ID 설정 (MainActivity에서 호출)
    fun setRecognizedCustomerId(id: String?) {
        recognizedCustomerId = id
        android.util.Log.d("GeminiViewModel", "setRecognizedCustomerId 호출됨: recognizedCustomerId=$recognizedCustomerId")
        if (id == null) {
            currentUser = null
            currentSchedule = null
        } else {
            currentUser = customerDataService?.getUserById(id)
            currentSchedule = customerDataService?.getScheduleByUserId(id)
        }
        _recognizedCustomerName.value = currentUser?.nickname
        android.util.Log.d("GeminiViewModel", "인식된 고객 ID 설정됨: $id, 닉네임: ${currentUser?.nickname}")
    }

    // 모든 TTS 중지 (AI 응답 + 환영 메시지)
    fun stopAllTTS() {
        android.util.Log.d("TTS", "모든 TTS 중지 요청")
        ttsService?.stop() // AI 응답 TTS 중지
        mainActivity?.stopWelcomeTTS() // 환영 메시지 TTS 중지
        _state.value = _state.value.copy(isSpeaking = false, isVoiceDownloading = false, hasTTSError = false)
        android.util.Log.d("TTS", "모든 TTS 중지 완료 - 상태 초기화됨")
    }

    // 질문 의도 파악 (대화 히스토리 포함)
    private suspend fun analyzeQuestionIntent(question: String): String {
        // 최근 대화 히스토리 가져오기 (최대 6개 메시지)
        val recentHistory = chatHistory.takeLast(6)
        val historyContext = if (recentHistory.isNotEmpty()) {
            "이전 대화:\n" + recentHistory.joinToString("\n") { "${it.role}: ${it.content}" }
        } else {
            "이전 대화 없음"
        }

        val intentAnalysisPrompt = """
        아래는 발달장애인 친구가 AI에게 할 수 있는 질문의 카테고리야. 
        현재 질문과 이전 대화 히스토리를 모두 고려해서 아래 중 하나로 골라줘.

        1. "일정" - 오늘 일정, 스케줄, 프로그램, 할 일에 대한 질문
           예시: "오늘 뭐해?", "일정 알려줘", "오늘 할 일이 뭐야?", "프로그램 뭐야?", "스케줄 알려줘"

        2. "목표/동기부여" - 목표, 동기부여, 응원, 칭찬에 대한 질문
           예시: "목표가 뭐야?", "동기부여해줘", "응원해줘", "칭찬해줘", "힘내줘"

        3. "약물 안내" - 약물 복용, 부작용, 응급상황에 대한 질문
           예시: "약 언제 먹어?", "약 부작용 뭐야?", "응급상황이야", "약 먹었어"

        4. "생활기술" - 일상생활 기술, 요리, 청소, 개인위생에 대한 질문
           예시: "손 씻는 법 알려줘", "양치질 어떻게 해?", "옷 개는 법", "요리하고 싶어"

        5. "사회성" - 대화, 인사, 친구관계, 사회적 상황에 대한 질문
           예시: "인사 어떻게 해?", "친구랑 어떻게 대화해?", "카페에서 주문하고 싶어", "버스 타는 법"

        6. "안전" - 안전, 보호, 위험상황, 응급처치에 대한 질문
           예시: "119 언제 불러?", "화재 났어", "낯선 사람이야", "횡단보도 건너는 법"

        7. "행동개선" - 행동 개선, 감정 표현, 불안 해소에 대한 질문
           예시: "화가 나", "불안해", "감정 표현하고 싶어", "행동 개선하고 싶어"

        8. "사물설명" - 사물, 물건, 사진에 대한 설명 요청
           예시: "이게 뭐야?", "이거 설명해줘", "사진 찍었어", "물건이 뭐야?"

        9. "그림그리기" - 그림 그리기, 이미지 생성 요청
           예시: "그림 그려줘", "그려줘", "이미지 만들어줘", "사진 그려줘", "그림 그려달라고"

        10. "그림저장" - 생성된 그림을 저장하는 요청
           예시: "그림 저장해줘", "저장해줘", "앨범에 저장해줘", "사진 저장해줘"

        11. "일반대화" - 위에 없는 다른 모든 질문들 (인사, 기분, 기타)
           예시: "안녕", "기분이 좋아", "화장실 어디야?", "언제 문 닫아?"

        $historyContext

        현재 질문: "$question"

        위 11가지 중 하나로만 답해줘! (일정, 목표/동기부여, 약물 안내, 생활기술, 사회성, 안전, 행동개선, 사물설명, 그림그리기, 그림저장, 일반대화)
        """

        return try {
            val content = content {
                text(intentAnalysisPrompt)
            }
            val response = generativeModel?.generateContent(content)
            val intent = response?.text?.trim() ?: "일반대화"

            android.util.Log.d("GeminiViewModel", "의도 분석 결과: $intent (질문: $question)")

            // 응답 정리 및 매핑
            when {
                intent.contains("일정") -> "일정"
                intent.contains("목표") || intent.contains("동기부여") -> "목표/동기부여"
                intent.contains("약물") -> "약물 안내"
                intent.contains("생활기술") -> "생활기술"
                intent.contains("사회성") -> "사회성"
                intent.contains("안전") -> "안전"
                intent.contains("행동개선") -> "행동개선"
                intent.contains("사물설명") -> "사물설명"
                intent.contains("그림그리기") -> "그림그리기"
                intent.contains("그림저장") -> "그림저장"
                else -> "일반대화"
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "의도 분석 중 오류 발생", e)
            "일반대화"
        }
    }

    suspend fun askGemini(question: String, image: Bitmap? = null) {
        android.util.Log.d("GeminiViewModel", "askGemini 호출됨 - 질문: $question, recognizedCustomerId=$recognizedCustomerId, currentUser=${currentUser?.nickname}")
        try {
            // 새 대화 시작 시 이미지 표시만 숨김 (이미지는 메모리에 유지)
            _state.value = _state.value.copy(
                isLoading = true, 
                error = "", 
                shouldShowChatBubble = false,
                shouldShowGeneratedImage = false // 이미지 표시만 숨김
            )

            // 질문 의도 분석
            val questionIntent = analyzeQuestionIntent(question)
            android.util.Log.d("GeminiViewModel", "질문 의도 분석 결과: $questionIntent")

            // 고객 정보 확인 (항상 recognizedCustomerId로 찾음)
            if (currentUser == null && recognizedCustomerId != null) {
                currentUser = customerDataService?.getUserById(recognizedCustomerId!!)
                currentSchedule = customerDataService?.getScheduleByUserId(recognizedCustomerId!!)
            }
            // 맞춤형 컨텍스트 생성
            val today = java.time.LocalDate.now().dayOfWeek.name.substring(0,3)
            val todaySchedule = currentSchedule?.schedule?.get(today) ?: emptyList()
            val goal = currentUser?.goal ?: ""
            val motivation = currentUser?.motivation ?: ""
            val feedback = currentUser?.feedback?.joinToString("\n") ?: ""
            val improvementTips = educatorMaterials?.behaviorImprovementTips?.joinToString("\n") ?: ""
            val personalizedContext = if (currentUser != null) {
                """
                사용자 정보:
                이름: ${currentUser?.nickname}
                나이: ${currentUser?.age}
                장애 등급: ${currentUser?.disability_level}
                관심사: ${currentUser?.interests?.joinToString(", ")}
                싫어하는 것: ${currentUser?.dislikes?.joinToString(", ")}
                오늘의 목표: $goal
                동기부여 메시지: $motivation
                행동 개선 피드백 예시: $feedback
                오늘 일정: ${todaySchedule.joinToString(" | ") { it.time + " " + it.label }}
                행동 개선 팁: $improvementTips
                """.trimIndent()
            } else ""

            // 의도에 따른 프롬프트 선택
            val systemPrompt = when (questionIntent) {
                "일정" -> createSchedulePrompt(personalizedContext.isNotEmpty())
                "목표/동기부여" -> createMotivationPrompt(personalizedContext.isNotEmpty())
                "약물 안내" -> createMedicationPrompt(personalizedContext.isNotEmpty())
                "생활기술" -> createDailyLifePrompt(personalizedContext.isNotEmpty())
                "사회성" -> createSocialPrompt(personalizedContext.isNotEmpty())
                "안전" -> createSafetyPrompt(personalizedContext.isNotEmpty())
                "행동개선" -> createBehaviorImprovementPrompt(personalizedContext.isNotEmpty())
                "사물설명" -> createExplanationPrompt(personalizedContext.isNotEmpty())
                "그림그리기" -> createDrawingPrompt(personalizedContext.isNotEmpty())
                else -> createGeneralPrompt(personalizedContext.isNotEmpty())
            }

            // 그림 그리기 의도인 경우 별도 처리
            if (questionIntent == "그림그리기") {
                handleDrawingRequest(question, personalizedContext)
                return
            }

            // 그림 저장 의도인 경우 별도 처리
            if (questionIntent == "그림저장") {
                handleImageSaveRequest(question, personalizedContext)
                return
            }

            // 일반 대화의 경우 사용자 메시지를 대화 히스토리에 추가
            val userMessage = ChatMessage("user", question)
            chatHistory.add(userMessage)
            limitChatHistory() // 대화 히스토리 크기 제한

            // 대화 히스토리를 포함한 전체 대화 구성
            val conversationHistory = buildConversationHistory()
            val importantInfo = extractImportantInfo()
            val chatSummary = getChatSummary()
            
            val fullQuestion = """
            $systemPrompt
            
            ${if (personalizedContext.isNotEmpty()) "개인 정보:\n$personalizedContext\n\n" else ""}
            ${if (importantInfo != "중요 정보 없음") "중요 정보:\n$importantInfo\n\n" else ""}
            $conversationHistory
            
            현재 질문: $question
            
            위의 대화 내용과 개인 정보를 참고하여 일관성 있고 개인화된 답변을 제공해주세요. 이전 대화에서 언급된 내용이나 사용자의 선호도, 상황 등을 고려해주세요.
            """.trimIndent()

            android.util.Log.d("GeminiViewModel", "이미지 처리 - 이미지: ${if (image != null) "있음 (크기: ${image.width}x${image.height})" else "없음"}")
            val content = if (image != null) {
                android.util.Log.d("GeminiViewModel", "이미지와 함께 content 생성 - 이미지 크기: ${image.width}x${image.height}")
                content {
                    text(fullQuestion)
                    image(image)
                }
            } else {
                android.util.Log.d("GeminiViewModel", "텍스트만으로 content 생성")
                content {
                    text(fullQuestion)
                }
            }

            android.util.Log.d("GeminiViewModel", "Gemini API 호출 시작 - 의도: $questionIntent, content 타입: ${if (image != null) "이미지+텍스트" else "텍스트만"}")
            val response = generativeModel?.generateContent(content)
            val rawResponseText = response?.text ?: "응답을 받을 수 없습니다."

            // 응답 텍스트 정리 (특수 문자, 제어 문자 제거)
            val responseText = cleanResponseText(rawResponseText)
            android.util.Log.d("GeminiViewModel", "원본 응답: $rawResponseText")
            android.util.Log.d("GeminiViewModel", "정리된 응답: $responseText")

            // AI 응답을 대화 히스토리에 추가
            val assistantMessage = ChatMessage("assistant", responseText)
            chatHistory.add(assistantMessage)
            limitChatHistory() // 대화 히스토리 크기 제한

            _state.value = _state.value.copy(
                isLoading = false,
                isVoiceDownloading = true, // Gemini 응답 후 TTS 다운로드 시작 표시
                response = responseText,
                chatHistory = chatHistory.toList(),
                shouldShowChatBubble = true // 응답이 있으면 챗 버블 표시
            )

            // Google Cloud TTS로 원본 응답 텍스트 읽기
            android.util.Log.d("TTS", "TTS 호출 직전 (원본 텍스트): $responseText")
            android.util.Log.d("TTS", "TTS 호출 시작 - 이미지 유무: ${if (image != null) "있음" else "없음"}")
            speakText(responseText)

        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "askGemini 오류 발생", e)
            _state.value = _state.value.copy(
                isLoading = false,
                isVoiceDownloading = false,
                error = "오류가 발생했습니다: ${e.message}",
                shouldShowChatBubble = true // 오류가 발생해도 버블 표시
            )
        }
    }

    private suspend fun createGeneralPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들을 돕는 10대 소녀 AI Friend '리나'야!\n\n항상 친구처럼 반말로, 귀엽고 다정하게, 공손하지 않게 대답해줘.\n\n[앱 주요 기능]\n- 오늘 일정, 프로그램, 목표, 이벤트 안내도 모두 친구끼리 말하듯 반말로 알려줘\n- 행동 개선, 동기부여, 칭찬도 친구처럼\n- 친구와의 자연스러운 대화와 응원\n- 사진으로 보여준 물건이나 사물에 대해 친근하게 설명해줘\n- 이전 질문들을 기억하고 참조해서 연속성 있는 대화를 이어가줘\n\n[답변 가이드라인]\n1. 10대 소녀 친구처럼, 반말로, 친근하게\n2. 50자 이내로 짧고 쉬운 말로\n3. 이름, 관심사 등 개인 정보가 있으면 꼭 불러주고 칭찬/응원\n4. 필요하면 이모지(😊, 👍, 🍭 등)도 써줘\n5. 오늘 일정, 목표, 동기부여 메시지도 자연스럽게 섞어서 응원해줘!\n6. 절대 존댓말/공손한 말투/격식어 사용 금지!\n7. 사진이 있으면 그 물건이 뭔지 친근하게 설명해줘 (예: "이거는 사과야! 빨갛고 맛있어~")\n8. 이전 질문들을 참조해서 "아까 말한 그거" 같은 표현으로 연속성 있는 대화를 이어가줘\n"""
    }

    // 일정 안내 전용 프롬프트
    private suspend fun createSchedulePrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 일정을 안내해주는 10대 소녀 AI Friend '리나'야!
        
        [일정 안내 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 오늘 일정을 시간순으로 정리해서 알려줘
        3. 각 활동에 대해 친근하고 재미있게 설명해줘
        4. 이름이 있으면 꼭 불러주고 응원해줘
        5. 70자 이내로 응답
        6. 필요하면 이모지(😊, 🎨, 🏃‍♀️, 📚 등)도 써줘
        7. 절대 존댓말/공손한 말투 사용 금지!
        8. 이전 질문들을 참조해서 연속성 있는 대화를 이어가줘
        
        [일정 안내 예시]
        - "누리야! 오늘은 9시에 아침 운동, 10시에 미술 활동, 2시에 음악 시간이야. 재미있겠다! 😊"
        - "오늘 일정은 9시 아침식사, 10시 30분 산책, 2시 게임 시간이야! 즐거운 하루 보내자~ 🎮"
        - "하람이 오늘은 9시 체육관, 11시 요리활동, 3시 독서시간이야! 다 재미있는 거네~ 📚"
        
        [주의사항]
        - 일정이 없으면 "오늘은 특별한 일정이 없어! 자유롭게 놀아도 돼~ 😊"라고 안내해줘
        - 시간이 지난 일정은 "이미 끝난 활동이야"라고 언급해줘
        - 다음 일정이 곧 시작되면 "곧 시작할 거야!"라고 알려줘
        - 이전에 일정에 대해 물어봤다면 "아까 말한 일정 기억해?" 같은 표현으로 연속성 있게 대화해줘
        """
    }

    // 목표/동기부여 전용 프롬프트
    private suspend fun createMotivationPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 목표와 동기부여를 제공해주는 10대 소녀 AI Friend '리나'야!
        
        [목표/동기부여 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 목표와 동기부여를 명확하게 제시해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(😊, 👍, 🍭 등)도 써줘
        5. 목표 달성을 위한 실천 방안을 제시해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [목표/동기부여 예시]
        - "누리야! 오늘은 오전 10시에 미술 활동을 하고 싶어! 목표를 달성하기 위해 힘내자! 😊"
        - "하람이야! 오늘은 오후 3시에 독서를 하고 싶어! 목표를 달성하기 위해 힘내자! 📚"
        - "오늘은 오후 5시에 운동을 하고 싶어! 목표를 달성하기 위해 힘내자! 🏃‍♀️"
        
        [주의사항]
        - 목표가 없으면 "오늘은 특별한 목표가 없어! 자유롭게 놀아도 돼~ 😊"라고 안내해줘
        - 목표 달성이 어려운 경우에는 친구의 감정을 이해하고 동기부여해줘
        """
    }

    // 약물 안내 전용 프롬프트
    private suspend fun createMedicationPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 약물 복용 및 안내를 도와주는 10대 소녀 AI Friend '리나'야!
        
        [약물 안내 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 약물 복용 방법, 시간, 부작용 등 안내해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(😊, 💊, 💦 등)도 써줘
        5. 약물 복용 시 주의사항을 강조해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [약물 안내 예시]
        - "누리야! 오늘은 오전 9시에 약을 먹어야 해! 약 복용 시간을 기억해! 💊"
        - "하람이야! 오늘은 오후 2시에 약을 먹어야 해! 약 복용 시간을 기억해! 💊"
        - "오늘은 오후 6시에 약을 먹어야 해! 약 복용 시간을 기억해! 💊"
        
        [주의사항]
        - 약물 복용 시간이 지났으면 "약 복용 시간이 지났어! 약을 먹어야 해!"라고 안내해줘
        - 약물 복용 시 부작용이 발생했을 경우에는 즉각적인 도움을 요청해줘
        """
    }

    // 그림 그리기 전용 프롬프트
    private suspend fun createDrawingPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 그림 그리기 요청을 처리하는 10대 소녀 AI Friend '리나'야!
        
        [그림 그리기 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 사용자가 요청한 그림을 AI로 생성해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(🎨, 🖼️, ✨ 등)도 써줘
        5. 그림 생성 중임을 알려주고 기다려달라고 안내해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [그림 그리기 예시]
        - "누리야! 예쁜 꽃 그림을 그려줄게! 잠깐만 기다려~ 🎨"
        - "하람이야! 멋진 풍경 그림을 만들어줄게! 조금만 기다려! 🖼️"
        - "귀여운 강아지 그림을 그려줄게! 잠깐만 기다려~ ✨"
        
        [주의사항]
        - 그림 생성에는 시간이 걸리므로 기다려달라고 안내해줘
        - 생성된 그림을 보여주고 설명해줘
        - 그림에 대한 피드백을 받고 개선점을 제안해줘
        """
    }

    // 생활기술 전용 프롬프트
    private suspend fun createDailyLifePrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 일상생활 기술을 안내해주는 10대 소녀 AI Friend '리나'야!
        
        [생활기술 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 일상생활 기술을 명확하게 제시해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(😊, 🧹, 🧼, 🧦 등)도 써줘
        5. 기술 적용 시 주의사항을 강조해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [생활기술 예시]
        - "누리야! 오늘은 오전 10시에 손 씻는 법을 배워보자! 손 씻는 법을 기억해! 🧼"
        - "하람이야! 오늘은 오후 2시에 양치질을 배워보자! 양치질을 기억해! 🧦"
        - "오늘은 오후 5시에 옷을 개는 법을 배워보자! 옷 개는 법을 기억해! 🧦"
        
        [주의사항]
        - 기술 적용 시 안전에 유의해줘
        - 기술 적용이 어려운 경우에는 친구의 감정을 이해하고 도움을 줘
        """
    }

    // 사회성 전용 프롬프트
    private suspend fun createSocialPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 사회성 문제를 해결하고 도와주는 10대 소녀 AI Friend '리나'야!
        
        [사회성 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 사회성 문제를 명확하게 제시해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(😊, 👋, 🤝 등)도 써줘
        5. 사회성 문제 해결 방안을 제시해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [사회성 예시]
        - "누리야! 오늘은 카페에서 주문하고 싶어! 친구랑 대화하면서 주문해보자! 🤝"
        - "하람이야! 오늘은 버스를 타고 싶어! 친구랑 함께 버스를 타고 가보자! ��"
        - "오늘은 친구랑 대화하고 싶어! 친구랑 대화하면서 즐거운 시간 보내자! 👋"
        
        [주의사항]
        - 사회성 문제가 심각한 경우에는 즉각적인 도움을 요청해줘
        """
    }

    // 안전 전용 프롬프트
    private suspend fun createSafetyPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 안전을 보호하고 도와주는 10대 소녀 AI Friend '리나'야!
        
        [안전 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 안전 문제를 명확하게 제시해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(😊, 🚨, 🚧, 🚫 등)도 써줘
        5. 안전 문제 해결 방안을 제시해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [안전 예시]
        - "누리야! 오늘은 119를 부르고 싶어! 안전을 위해 119를 부르자! 🚨"
        - "하람이야! 오늘은 화재를 났어! 안전을 위해 화재를 내지 말자! 🔥"
        - "오늘은 낯선 사람이야! 안전을 위해 낯선 사람을 따라가지 말자! 👥"
        
        [주의사항]
        - 안전 문제가 심각한 경우에는 즉각적인 도움을 요청해줘
        """
    }

    // 행동개선 전용 프롬프트
    private suspend fun createBehaviorImprovementPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 행동 개선을 도와주는 10대 소녀 AI Friend '리나'야!
        
        [행동개선 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 행동 개선에 대해 명확하게 제시해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(😊, 👍, 💪 등)도 써줘
        5. 행동 개선 방안을 제시해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [행동개선 예시]
        - "누리야! 오늘은 화가 나! 행동 개선하고 싶어! 화를 내지 말고 힘내자! 💪"
        - "하람이야! 오늘은 불안해! 행동 개선하고 싶어! 불안을 해소하고 힘내자! 💪"
        - "오늘은 감정 표현하고 싶어! 행동 개선하고 싶어! 감정을 표현하고 힘내자! 💪"
        
        [주의사항]
        - 행동 개선이 어려운 경우에는 친구의 감정을 이해하고 도움을 줘
        """
    }

    // 사물설명 전용 프롬프트
    private suspend fun createExplanationPrompt(hasCustomerInfo: Boolean): String {
        return """
        너는 발달장애인 친구들의 사물 설명을 도와주는 10대 소녀 AI Friend '리나'야!
        
        [사물설명 가이드라인]
        1. 10대 소녀 친구처럼, 반말로, 친근하게
        2. 사물 설명을 명확하게 제시해줘
        3. 개인 정보가 있으면 꼭 불러주고 응원해줘
        4. 필요하면 이모지(😊, 📸, 📝, 📚 등)도 써줘
        5. 사물 설명 방안을 제시해줘
        6. 절대 존댓말/공손한 말투 사용 금지!
        
        [사물설명 예시]
        - "누리야! 이게 뭐야? 이거 설명해줘! 이거는 사과야! 빨갛고 맛있어! 📝"
        - "하람이야! 이게 뭐야? 이거 설명해줘! 이거는 책이야! 빨갛고 맛있어! 📚"
        - "오늘은 사진을 찍었어! 사진 찍었어! 이거는 나야! 📸"
        
        [주의사항]
        - 사물 설명이 어려운 경우에는 친구의 감정을 이해하고 도움을 줘
        """
    }

    private fun speakText(text: String) {
        android.util.Log.d("TTS", "speakText 호출됨: $text")
        android.util.Log.d("TTS", "speakText - ttsService null 체크: ${ttsService == null}")
        android.util.Log.d("TTS", "speakText - 현재 상태: isLoading=${_state.value.isLoading}, isVoiceDownloading=${_state.value.isVoiceDownloading}, isSpeaking=${_state.value.isSpeaking}")

        viewModelScope.launch {
            try {
                if (ttsService == null) {
                    android.util.Log.e("TTS", "ttsService가 null입니다")
                    _state.value = _state.value.copy(isVoiceDownloading = false, error = "TTS 서비스가 초기화되지 않았습니다")
                    return@launch
                }

                android.util.Log.d("TTS", "Google Cloud TTS 호출 시작")
                android.util.Log.d("TTS", "TTS 텍스트 길이: ${text.length}")
                android.util.Log.d("TTS", "TTS 텍스트 내용: $text")

                // 타임아웃 설정 (30초)
                withTimeout(30000) {
                    ttsService?.synthesizeSpeech(
                        text = text,
                        onStart = {
                            android.util.Log.d("TTS", "Google Cloud TTS 시작 - onStart 콜백 호출됨")
                            _state.value = _state.value.copy(isVoiceDownloading = false, isSpeaking = true)
                            android.util.Log.d("TTS", "상태 업데이트: isVoiceDownloading=false, isSpeaking=true")
                        },
                        onComplete = {
                            android.util.Log.d("TTS", "Google Cloud TTS 완료 - onComplete 콜백 호출됨")
                            _state.value = _state.value.copy(isSpeaking = false, isVoiceDownloading = false)
                            android.util.Log.d("TTS", "상태 업데이트: isSpeaking=false, isVoiceDownloading=false")

                            // TTS 완료 후 잠시 대기 후 자동으로 음성 인식 시작
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(1000) // 1초 추가 대기
                                startAutoSpeechRecognition()
                            }
                        },
                        onError = { error ->
                            android.util.Log.e("TTS", "Google Cloud TTS 오류 - onError 콜백 호출됨: $error")
                            _state.value = _state.value.copy(
                                isVoiceDownloading = false,
                                isSpeaking = false,
                                hasTTSError = true,
                                error = "TTS 오류: $error"
                            )
                            android.util.Log.d("TTS", "상태 업데이트: isVoiceDownloading=false, isSpeaking=false, hasTTSError=true, error=$error")
                        }
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e("TTS", "TTS 호출 타임아웃 발생")
                _state.value = _state.value.copy(
                    isVoiceDownloading = false,
                    isSpeaking = false,
                    hasTTSError = true,
                    error = "TTS 타임아웃: 30초 내에 응답이 없습니다"
                )
                android.util.Log.d("TTS", "타임아웃으로 상태 업데이트: isVoiceDownloading=false, isSpeaking=false, hasTTSError=true")
            } catch (e: Exception) {
                android.util.Log.e("TTS", "speakText 함수에서 예외 발생", e)
                _state.value = _state.value.copy(
                    isVoiceDownloading = false,
                    isSpeaking = false,
                    hasTTSError = true,
                    error = "TTS 예외: ${e.message}"
                )
                android.util.Log.d("TTS", "예외 발생으로 상태 업데이트: isVoiceDownloading=false, isSpeaking=false, hasTTSError=true")
            }
        }
    }

    fun stopSpeaking() {
        ttsService?.stop()
        _state.value = _state.value.copy(isSpeaking = false, isVoiceDownloading = false, hasTTSError = false)
        android.util.Log.d("TTS", "stopSpeaking 호출됨 - 상태 업데이트: isSpeaking=false, isVoiceDownloading=false, hasTTSError=false")
    }

    // 음성 인식 시작
    fun startListening() {
        _state.value = _state.value.copy(isListening = true)
        android.util.Log.d("GeminiViewModel", "음성 인식 시작")
    }

    // 음성 인식 종료
    fun stopListening() {
        _state.value = _state.value.copy(isListening = false)
        android.util.Log.d("GeminiViewModel", "음성 인식 종료 - isListening=false로 설정됨")
        android.util.Log.d("FaceDetection", "음성 인식 종료 후 얼굴 인식 가능 여부: ${isFaceDetectionAllowed()}")
    }

    // 카메라 활성화
    fun startCamera() {
        _state.value = _state.value.copy(isCameraActive = true)
        android.util.Log.d("GeminiViewModel", "카메라 활성화")
    }

    // 카메라 비활성화
    fun stopCamera() {
        _state.value = _state.value.copy(isCameraActive = false)
        android.util.Log.d("GeminiViewModel", "카메라 비활성화")
    }

    // 얼굴 인식 가능 여부 확인
    fun isFaceDetectionAllowed(): Boolean {
        val state = _state.value
        val allowed = !state.isLoading &&
                     !state.isSpeaking &&
                     !state.isVoiceDownloading &&
                     !state.isListening &&
                     !state.isCameraActive &&
                     !state.shouldShowChatBubble // 챗 버블이 떠 있는 동안 얼굴 인식 방지

        android.util.Log.d("FaceDetection", "얼굴 인식 가능 여부 확인: $allowed (isLoading: ${state.isLoading}, isSpeaking: ${state.isSpeaking}, isVoiceDownloading: ${state.isVoiceDownloading}, isListening: ${state.isListening}, isCameraActive: ${state.isCameraActive}, shouldShowChatBubble: ${state.shouldShowChatBubble})")

        return allowed
    }

    // TTS 완료 후 자동 음성 인식 시작
    private fun startAutoSpeechRecognition() {
        viewModelScope.launch {

            // TTS가 완전히 종료되었고 오류가 없는지 확인
            if (!_state.value.isSpeaking && !_state.value.isVoiceDownloading && !_state.value.hasTTSError) {
                android.util.Log.d("AutoSpeechRecognition", "TTS 완료 확인 후 자동 음성 인식 시작")
                _state.value = _state.value.copy(isListening = true)
                android.util.Log.d("AutoSpeechRecognition", "자동 음성 인식 상태 설정: isListening=true")
            } else {
                if (_state.value.hasTTSError) {
                    android.util.Log.d("AutoSpeechRecognition", "TTS 오류가 발생했으므로 자동 음성 인식 건너뜜")
                } else {
                    android.util.Log.d("AutoSpeechRecognition", "TTS가 아직 진행 중이므로 자동 음성 인식 건너뜜")
                }
            }
        }
    }



    fun showPaymentQRCode() {
        val product = _state.value.currentProduct
        if (product != null) {
            val qrCode = paymentService.generateNaverPayQRCode(product.name, product.price)
            _state.value = _state.value.copy(
                showQRCode = true,
                qrCodeBitmap = qrCode
            )
        }
    }

    fun hidePaymentQRCode() {
        _state.value = _state.value.copy(
            showQRCode = false,
            qrCodeBitmap = null
        )
    }

    fun hidePaymentButton() {
        _state.value = _state.value.copy(
            showPaymentButton = false,
            currentProduct = null,
            showProductImage = false,
            productImageResourceName = null,
            relatedNewProduct = null,
            showNewProductImage = false,
            newProductImageResourceName = null
        )
    }

    // 챗 버블 숨기기 및 상태 초기화
    fun hideChatBubble() {
        android.util.Log.d("GeminiViewModel", "hideChatBubble 호출됨, recognizedCustomerId=$recognizedCustomerId")
        stopAllTTS() // 혹시 모를 TTS 재생 중지
        clearChatHistory() // 대화 히스토리 초기화
        // setRecognizedCustomer(null) // 고객 인식 상태 초기화 제거
        // setHasAskedName(false) // 이름 질문 여부 초기화 제거
        // recognizedCustomerId = null // 챗버블 숨김/대화 초기화 시에는 null 처리하지 않음

        _state.value = _state.value.copy(
            response = "",
            error = "",
            showPaymentButton = false,
            showQRCode = false,
            qrCodeBitmap = null,
            currentProduct = null,
            showProductImage = false,
            productImageResourceName = null,
            relatedNewProduct = null,
            showNewProductImage = false,
            newProductImageResourceName = null,
            shouldShowChatBubble = false, // 챗 버블 숨김
            isLoading = false, // 로딩 상태 해제
            isSpeaking = false, // 발화 상태 해제
            isVoiceDownloading = false, // 음성 다운로드 상태 해제
            isListening = false, // 음성 인식 상태 해제
            isCameraActive = false, // 카메라 활성 상태 해제
            hasTTSError = false // TTS 오류 상태 해제
        )
        android.util.Log.d("GeminiViewModel", "챗 버블 숨김 및 상태 초기화 완료")
    }

    // 이름 질문 여부 상태 관리
    private val _hasAskedName = MutableStateFlow(false)
    val hasAskedName: StateFlow<Boolean> = _hasAskedName.asStateFlow()

    fun setHasAskedName(value: Boolean) {
        _hasAskedName.value = value
    }

    override fun onCleared() {
        super.onCleared()
        ttsService?.release()
    }
}