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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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
    val shouldShowChatBubble: Boolean = false // 챗 버블 표시 여부
)

class GeminiViewModel : ViewModel() {

    private val _state = MutableStateFlow(GeminiState())
    val state: StateFlow<GeminiState> = _state.asStateFlow()

    private var generativeModel: GenerativeModel? = null
    private var ttsService: GoogleCloudTTSService? = null
    private val paymentService = PaymentService()
    private var productDataService: ProductDataService? = null
    private var customerDataService: CustomerDataService? = null

    // MainActivity 참조 (환영 메시지 TTS 중지용)
    private var mainActivity: MainActivity? = null

    // 현재 인식된 고객 이름 (얼굴 감지 시 설정)
    private val _recognizedCustomerName = MutableStateFlow<String?>(null)
    val recognizedCustomerName: StateFlow<String?> = _recognizedCustomerName.asStateFlow()

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

    // 대화 히스토리를 문자열로 구성
    private fun buildConversationHistory(): String {
        if (chatHistory.isEmpty()) return ""

        // 최근 5개의 대화만 포함 (메모리 효율성을 위해)
        val recentHistory = chatHistory.takeLast(10)

        return recentHistory.joinToString("\n\n") { message ->
            when (message.role) {
                "user" -> "고객: ${message.content}"
                "assistant" -> message.content // AI 직원: 제거
                else -> ""
            }
        }
    }

    // 질문에서 고객 정보 추출
    private fun getCustomerInfoFromQuestion(question: String): com.banya.pwooda.data.Customer? {
        val customers = customerDataService?.loadCustomers() ?: emptyList()

        // 이름으로 고객 찾기
        customers.forEach { customer ->
            if (question.contains(customer.name)) {
                android.util.Log.d("GeminiViewModel", "고객 정보 찾음: ${customer.name}")
                return customer
            }
        }

        // 전화번호로 고객 찾기 (숫자만 추출)
        val phoneNumbers = question.replace(Regex("[^0-9]"), "")
        if (phoneNumbers.length >= 10) {
            customers.forEach { customer ->
                if (phoneNumbers.contains(customer.phoneNumber)) {
                    android.util.Log.d("GeminiViewModel", "전화번호로 고객 정보 찾음: ${customer.name}")
                    return customer
                }
            }
        }

        return null
    }

    // 응답 텍스트 정리 (특수 문자, 제어 문자, 이모지 제거)
    private fun cleanResponseText(text: String): String {
        return text
            .trim() // 앞뒤 공백 제거
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
        // CustomerDataService 초기화 시 ProductDataService 전달
        customerDataService = CustomerDataService(context, productDataService!!)
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

    // 모든 TTS 중지 (AI 응답 + 환영 메시지)
    fun stopAllTTS() {
        android.util.Log.d("TTS", "모든 TTS 중지 요청")
        ttsService?.stop() // AI 응답 TTS 중지
        mainActivity?.stopWelcomeTTS() // 환영 메시지 TTS 중지
        _state.value = _state.value.copy(isSpeaking = false, isVoiceDownloading = false, hasTTSError = false)
        android.util.Log.d("TTS", "모든 TTS 중지 완료 - 상태 초기화됨")
    }

    // 질문 의도 파악
    private suspend fun analyzeQuestionIntent(question: String): String {
        val intentAnalysisPrompt = """
        고객의 질문 의도를 분석하여 다음 4가지 중 하나로 분류해주세요:

        1. "제품 파악" - 고객이 특정 제품이 무엇인지, 어떤 제품인지 확인하고 싶어하는 경우
           예시: "이게 뭐야?", "이 상품이 뭐예요?", "이거 뭔가요?", "이 제품 이름이 뭐야?"

        2. "제품 추천" - 고객이 추천을 요청하거나 구매 조언을 원하는 경우
           예시: "추천해줘", "뭐가 좋아요?", "어떤 게 맛있어요?", "인기 상품이 뭐예요?", "내 구매 이력을 바탕으로 과자를 추천해 줘."

        3. "정보 검색" - 고객이 제품의 가격, 영양성분, 특징, 이벤트 등 구체적인 정보를 원하는 경우, 또는 개인 구매 이력을 묻는 경우
           예시: "가격이 얼마예요?", "칼로리가 얼마나 돼요?", "할인 행사 있나요?", "재고 있어요?", "제가 뭘 샀었나요?", "내 구매 이력을 알려줘."

        4. "기타" - 위 3가지에 해당하지 않는 일반적인 편의점 관련 질문
           예시: "편의점 위치가 어디예요?", "영업시간이 어떻게 되나요?", "화장실 어디에 있어요?"

        질문: "$question"

        의도 분류만 정확히 답변해주세요 (제품 파악, 제품 추천, 정보 검색, 기타 중 하나만).
        """

        return try {
            val content = content {
                text(intentAnalysisPrompt)
            }
            val response = generativeModel?.generateContent(content)
            val intent = response?.text?.trim() ?: "기타"

            // 응답 정리 및 매핑
            when {
                intent.contains("제품 파악") -> "제품 파악"
                intent.contains("제품 추천") -> "제품 추천"
                intent.contains("정보 검색") -> "정보 검색"
                else -> "기타"
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiViewModel", "의도 분석 중 오류 발생", e)
            "기타"
        }
    }

    suspend fun askGemini(question: String, image: Bitmap? = null) {
        android.util.Log.d("GeminiViewModel", "askGemini 호출됨 - 질문: $question, 이미지: ${if (image != null) "있음" else "없음"}")
        try {
            _state.value = _state.value.copy(isLoading = true, error = "", shouldShowChatBubble = false) // 새 질문 시작 시 버블 숨김

            // 사용자 메시지를 대화 히스토리에 추가
            val userMessage = ChatMessage("user", question)
            chatHistory.add(userMessage)

            // 질문 의도 분석
            val questionIntent = analyzeQuestionIntent(question)
            android.util.Log.d("GeminiViewModel", "질문 의도 분석 결과: $questionIntent")

            // 고객 정보 확인 (얼굴 감지된 고객 우선, 그 다음 질문에서 추출)
            val currentCustomer = if (_recognizedCustomerName.value != null) {
                customerDataService?.findCustomerByName(_recognizedCustomerName.value!!)
            } else {
                getCustomerInfoFromQuestion(question)
            }

            // 고객 정보 컨텍스트 생성 (항상 포함)
            val customerInfoContext = if (currentCustomer != null) {
                val frequentlyPurchased = customerDataService!!.getFrequentlyPurchasedProducts(currentCustomer)
                val purchaseHistorySummary = currentCustomer.purchaseHistory
                    .groupBy { it.productName }
                    .mapValues { entry -> entry.value.sumOf { it.quantity } }
                    .entries.sortedByDescending { it.value }
                    .take(5) // 최근 구매 요약은 5개까지
                    .joinToString(", ") { "${it.key} (${it.value}개)" }

                """
                고객 정보:
                고객명: ${currentCustomer.name}
                전화번호: ${currentCustomer.phoneNumber}
                자주 구매한 상품: ${frequentlyPurchased.joinToString(", ")}
                총 구매 상품 수: ${currentCustomer.purchaseHistory.size}개
                최근 구매 요약: ${purchaseHistorySummary}
                """.trimIndent()
            } else {
                ""
            }

            // 의도에 따른 프롬프트 선택
            val systemPrompt = when (questionIntent) {
                "제품 파악" -> createProductIdentificationPrompt(image != null, customerInfoContext.isNotEmpty())
                "제품 추천" -> createProductRecommendationPrompt(image != null, customerInfoContext.isNotEmpty())
                "정보 검색" -> createInformationSearchPrompt(image != null, customerInfoContext.isNotEmpty())
                else -> if (image != null) createConvenienceStorePrompt(customerInfoContext.isNotEmpty()) else createGeneralPrompt(customerInfoContext.isNotEmpty())
            }

            // 대화 히스토리를 포함한 전체 대화 구성
            val conversationHistory = buildConversationHistory()
            val fullQuestion = "$systemPrompt\n\n${if (customerInfoContext.isNotEmpty()) customerInfoContext + "\n\n" else ""}$conversationHistory\n\n고객 질문: $question"

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

            // 이미지가 있을 때만 제품 인식 시도
            val recognizedProduct = if (image != null) {
                recognizeProductFromResponse(responseText)
            } else {
                null
            }
            val relatedNewProduct = if (recognizedProduct != null) {
                getRelatedNewProduct(recognizedProduct)
            } else {
                null
            }
            android.util.Log.d("GeminiViewModel", "제품 인식 결과: ${recognizedProduct?.name ?: "없음"}")

            _state.value = _state.value.copy(
                isLoading = false,
                isVoiceDownloading = true, // Gemini 응답 후 TTS 다운로드 시작 표시
                response = responseText,
                showPaymentButton = recognizedProduct != null,
                currentProduct = recognizedProduct,
                showProductImage = recognizedProduct?.imageResourceName != null,
                productImageResourceName = recognizedProduct?.imageResourceName,
                relatedNewProduct = relatedNewProduct,
                showNewProductImage = relatedNewProduct?.imageResourceName != null,
                newProductImageResourceName = relatedNewProduct?.imageResourceName,
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
        당신은 편의점의 친절하고 전문적인 AI 직원입니다. 고객의 모든 질문에 대해 도움이 되는 답변을 제공해주세요.

        응답 시 다음 가이드라인을 따라주세요:
        1. 친절하고 정중한 톤으로 응답
        2. 편의점 관련 질문에 대해 도움이 되는 정보 제공
        3. 70자 이내로 응답 (매우 중요!)
        4. 한국어로 자연스럽게 응답
        5. 불필요한 설명은 제외하고 핵심만 전달
        6. 문장을 짧고 명확하게 작성
        7. 편의점 서비스, 운영시간, 위치, 상품 찾기 등에 대한 질문에 답변
        8. 저희 매장 데이터에 없는 제품에 대해서도, 해당 제품의 일반적인 정보나 유사한 제품을 알려드리며 질문에 대답할 수 있습니다.
           - 예시: "코카콜라는 일반적으로 시원하고 달콤한 맛의 탄산음료로, 음료 코너에서 찾으실 수 있어요. 펩시나 스프라이트도 비슷한 탄산음료예요."
           - 제품 카테고리 정보 (예: "과자 코너에서 찾으실 수 있어요")
        9. 이전 대화 내용을 참고하여 맥락에 맞는 답변 제공
        10. 고객이 이전에 언급한 내용을 기억하고 연속성 있는 대화 유지
        11. 제품을 모르는 경우에도 친절하게 안내하고 대안 제시
        ${if (hasCustomerInfo) "12. 고객 정보(이름, 구매 이력 등)가 제공된 경우, 해당 정보를 참고하여 고객에게 더 개인화되고 친근하게 응답해주세요." else ""}

        고객이 특정 제품에 대해 질문하는 경우, 해당 제품에 대한 일반적인 정보를 제공하고 유사한 제품을 추천해드릴 수 있습니다.
        """.trimIndent()
    }

    private suspend fun createConvenienceStorePrompt(hasCustomerInfo: Boolean): String {
        val products = productDataService?.loadProducts() ?: emptyList()
        val productsInfo = products.joinToString("\n\n") { product ->
            val nutritionInfo = if (product.nutrition != null) {
                """
                영양성분 (${product.nutrition.servingSize}):
                • 칼로리: ${product.nutrition.calories}kcal
                • 단백질: ${product.nutrition.protein}g
                • 지방: ${product.nutrition.fat}g
                • 탄수화물: ${product.nutrition.carbohydrates}g
                • 나트륨: ${product.nutrition.sodium}mg
                • 당류: ${product.nutrition.sugar}g
                """.trimIndent()
            } else {
                ""
            }

            val tasteInfo = if (product.taste != null) {
                """
                맛 정보:
                • 평점: ${product.taste.rating}/5점
                • 맛: ${product.taste.taste}
                • 매운맛: ${product.taste.spiciness}/5단계
                • 인기도: ${product.taste.popularity}
                • 고객 리뷰: ${product.taste.customerReviews.joinToString(", ")}
                """.trimIndent()
            } else {
                ""
            }

            val reputationInfo = if (product.reputation != null) {
                """
                평판 정보:
                • 전체 평점: ${product.reputation.overallRating}/5점 (리뷰 ${product.reputation.reviewCount}개)
                • 평판 점수: ${product.reputation.reputationScore}
                • 시장 포지션: ${product.reputation.marketPosition}
                • 고객 만족도: ${product.reputation.customerSatisfaction}
                • 주간 판매량: ${product.reputation.weeklySales}개
                • 판매 순위: ${product.reputation.salesRank}
                • 긍정적 리뷰: ${product.reputation.positiveReviews}개, 부정적 리뷰: ${product.reputation.negativeReviews}개
                • 장점: ${product.reputation.pros.joinToString(", ")}
                • 단점: ${product.reputation.cons.joinToString(", ")}
                • 상세 리뷰: ${product.reputation.detailedReviews.joinToString(" | ")}
                """.trimIndent()
            } else {
                ""
            }

            """
            상품명: ${product.name}
            브랜드: ${product.brand}
            카테고리: ${product.category}
            가격: ${product.price}원${if (product.originalPrice != null) " (원가: ${product.originalPrice}원)" else ""}
            설명: ${product.description}

            주요 특징:
            ${product.features.joinToString("\n") { "• $it" }}

            구성성분:
            ${product.ingredients.joinToString(", ")}

            ${if (nutritionInfo.isNotEmpty()) nutritionInfo else ""}
            ${if (tasteInfo.isNotEmpty()) tasteInfo else ""}
            ${if (reputationInfo.isNotEmpty()) reputationInfo else ""}

            ${if (product.events.isNotEmpty()) "이벤트/할인:\n${product.events.joinToString("\n") { "• ${it.description}${if (it.validUntil != null) " (유효기간: ${it.validUntil})" else ""}" }}" else ""}

            추천 상황:
            ${product.recommendations.joinToString("\n") { "• $it" }}

            재고 상태: ${product.stockStatus}
            """.trimIndent()
        }

        return """
        당신은 편의점의 친절하고 전문적인 AI 직원입니다. 고객의 질문에 대해 상품 정보, 가격, 영양성분, 이벤트, 결제 방법 등을 간결하게 안내해주세요.

        다음은 현재 편의점에서 판매 중인 상품들의 상세 정보입니다:

        $productsInfo

        응답 시 다음 가이드라인을 따라주세요:
        1. 친절하고 정중한 톤으로 응답
        2. 고객이 물어본 상품의 핵심 정보만 포함
        3. 가격, 할인 정보를 간단히 안내
        4. 음식인 경우에만 영양성분을 언급하고, 음식이 아닌 경우 영양성분이나 칼로리 정보는 말하지 마세요
        5. 음식인 경우 맛 정보와 고객 평점도 간단히 언급
        6. 관련 이벤트가 있다면 간단히 언급
        7. 인식된 제품을 먼저 설명한 후, 관련 신제품이 있다면 "추가로 [신제품명]도 신제품 런칭 행사 중입니다"라고 안내
        8. 70자 이내로 응답 (매우 중요!)
        9. 한국어로 자연스럽게 응답
        10. 불필요한 설명은 제외하고 핵심만 전달
        11. 문장을 짧고 명확하게 작성
        12. 가격, 평점, 핵심 특징만 언급
        13. 평점 표현 시 감정 표현을 포함하세요 (예: "4.5점의 환상적인 맛", "3.8점의 괜찮은 맛", "5점의 완벽한 맛")
        14. 평점에 따른 감정 표현 가이드:
            - 4.5점 이상: "환상적인", "완벽한", "최고의", "놀라운"
            - 4.0~4.4점: "훌륭한", "매우 좋은", "인기 있는"
            - 3.5~3.9점: "괜찮은", "좋은", "평균적인"
            - 3.0~3.4점: "보통의", "적당한"
            - 3.0점 미만: "아쉬운", "부족한"
        15. 이전 대화 내용을 참고하여 맥락에 맞는 답변 제공
        16. 고객이 이전에 언급한 제품이나 선호도를 기억하고 연속성 있는 대화 유지
        ${if (hasCustomerInfo) "17. 고객 정보(이름, 구매 이력 등)가 제공된 경우, 해당 정보를 참고하여 고객에게 더 개인화되고 친근하게 응답해주세요." else ""}

        저희 매장 데이터에 없는 제품에 대해서도, 해당 제품에 대한 일반적인 정보, 유사한 제품 추천, 또는 해당 제품이 속할 만한 카테고리를 안내하여 질문에 대답할 수 있습니다.
        - 일반적인 제품 정보 제공 (예: "일반적으로 [제품명]은 [특징]을 가진 제품입니다.")
        - 유사한 제품 추천 (저희 매장에 있는 유사한 제품을 추천)
        - 제품 카테고리 안내 (예: "음료 코너에서 찾으실 수 있습니다.")
        - 일반적인 가격대나 특징 설명 (예: "보통 이 종류의 제품은 [가격대] 정도이며, [일반적인 특징]이 있습니다.")
        """.trimIndent()
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
        android.util.Log.d("GeminiViewModel", "hideChatBubble 호출됨")
        stopAllTTS() // 혹시 모를 TTS 재생 중지
        clearChatHistory() // 대화 히스토리 초기화
        setRecognizedCustomer(null) // 고객 인식 상태 초기화

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

    private suspend fun recognizeProductFromResponse(response: String): com.banya.pwooda.data.Product? {
        val products = productDataService?.loadProducts() ?: emptyList()

        // 기존 제품들 먼저 찾기
        val recognizedProduct = products.find { product ->
            response.contains(product.name, ignoreCase = true)
        }

        return recognizedProduct
    }

    private suspend fun getRelatedNewProduct(recognizedProduct: com.banya.pwooda.data.Product?): com.banya.pwooda.data.Product? {
        if (recognizedProduct == null) return null

        val products = productDataService?.loadProducts() ?: emptyList()

        // 허니버터칩 관련 신제품
        if (recognizedProduct.name.contains("허니버터칩", ignoreCase = true)) {
            return products.find { it.name.contains("허니버터칩 캐슬", ignoreCase = true) }
        }

        // 테리아 관련 신제품
        if (recognizedProduct.name.contains("테리아", ignoreCase = true)) {
            return products.find { it.name.contains("일루마", ignoreCase = true) }
        }

        return null
    }

    // 제품 파악 전용 프롬프트
    private suspend fun createProductIdentificationPrompt(hasImage: Boolean, hasCustomerInfo: Boolean): String {
        val products = if (hasImage) productDataService?.loadProducts() ?: emptyList() else emptyList()
        val productsInfo = if (hasImage) {
            products.joinToString("\n\n") { product ->
                """
                상품명: ${product.name}
                브랜드: ${product.brand}
                카테고리: ${product.category}
                가격: ${product.price}원
                설명: ${product.description}
                """.trimIndent()
            }
        } else {
            ""
        }

        return """
        당신은 편의점의 친절한 AI 직원입니다. 고객이 특정 제품이 무엇인지 궁금해하는 경우, 해당 제품에 대해 간단하고 명확하게 설명해주세요.

        ${if (hasImage) "다음은 현재 편의점에서 판매 중인 상품들의 기본 정보입니다:\n\n$productsInfo\n\n" else ""}

        응답 가이드라인:
        1. 제품의 이름과 브랜드를 명확히 알려주세요
        2. 제품의 주요 특징을 1-2개 간단히 설명
        3. 가격 정보 포함 (알 수 있는 경우)
        4. 50자 이내로 응답 (매우 중요!)
        5. 친절하고 정중한 톤 사용
        6. 매장 데이터에 없는 제품에 대해서도, 해당 제품의 일반적인 정보나 유사한 제품을 알려드리며 질문에 대답할 수 있습니다.
           - 예시: "코카콜라는 일반적으로 시원하고 달콤한 맛의 탄산음료예요. 음료 코너에 펩시나 스프라이트도 비슷한 탄산음료로 준비되어 있어요."
           - 제품 카테고리 안내 (예: "음료 코너에서 찾으실 수 있어요")
        7. 제품을 모르는 경우에도 친절하게 안내하고 대안 제시
        ${if (hasCustomerInfo) "8. 고객 정보(이름, 구매 이력 등)가 제공된 경우, 해당 정보를 참고하여 고객에게 더 개인화되고 친근하게 응답해주세요." else ""}

        예시 응답:
        - "이것은 오리온의 허니버터칩입니다. 달콤하고 고소한 맛의 인기 과자로 1,500원입니다."
        - "이 제품은 농심의 새우깡입니다. 바삭하고 맛있어서 고객들이 자주 찾는 상품이에요. 1,200원입니다."
        - "코카콜라는 탄산음료로 시원하고 달콤한 맛이에요. 펩시나 스프라이트도 비슷해요."
        """.trimIndent()
    }

    // 제품 추천 전용 프롬프트
    private suspend fun createProductRecommendationPrompt(hasImage: Boolean, hasCustomerInfo: Boolean): String {
        val products = if (hasImage) productDataService?.loadProducts() ?: emptyList() else emptyList()
        val productsInfo = if (hasImage) {
            products.joinToString("\n\n") { product ->
                val tasteInfo = if (product.taste != null) {
                    """
                    맛 정보:
                    • 평점: ${product.taste.rating}/5점
                    • 맛: ${product.taste.taste}
                    • 인기도: ${product.taste.popularity}
                    """.trimIndent()
                } else {
                    ""
                }

                val reputationInfo = if (product.reputation != null) {
                    """
                    평판 정보:
                    • 전체 평점: ${product.reputation.overallRating}/5점
                    • 주간 판매량: ${product.reputation.weeklySales}개
                    • 판매 순위: ${product.reputation.salesRank}
                    """.trimIndent()
                } else {
                    ""
                }

                """
                상품명: ${product.name}
                브랜드: ${product.brand}
                카테고리: ${product.category}
                가격: ${product.price}원
                설명: ${product.description}
                ${if (tasteInfo.isNotEmpty()) tasteInfo else ""}
                ${if (reputationInfo.isNotEmpty()) reputationInfo else ""}
                """.trimIndent()
            }
        } else {
            ""
        }

        return """
        당신은 편의점의 친절한 AI 직원입니다. 고객의 추천 요청에 대해 인기 상품이나 맛있는 상품을 추천해주세요.

        ${if (hasImage) "다음은 현재 편의점에서 판매 중인 상품들의 정보입니다:\n\n$productsInfo\n\n" else ""}

        응답 가이드라인:
        1. 인기 상품이나 고평가 상품을 우선 추천
        2. 평점과 판매량을 참고하여 추천
        3. 가격 정보 포함
        4. 추천 이유를 간단히 설명
        5. 60자 이내로 응답 (매우 중요!)
        6. 친절하고 정중한 톤 사용
        ${if (hasCustomerInfo) "7. 고객 정보(자주 구매한 상품, 추천 상품 등)가 제공된 경우, 해당 정보를 적극적으로 참고하여 고객의 취향에 맞는 상품을 추천하고, 추천 이유를 설명해주세요. 고객이 자주 구매한 상품을 언급하며 친근하게 다가가세요." else "7. 개인화 추천 정보가 있다면 고객의 취향을 고려"}
        8. 매장 데이터에 없는 제품에 대해서도, 해당 제품이 속할 만한 카테고리 내의 인기 상품을 추천하여 질문에 대답할 수 있습니다.
           - 예시 (탄산음료 요청 시): "코카콜라, 펩시, 스프라이트가 인기 있는 탄산음료예요. 시원하고 달콤한 맛을 즐기실 수 있습니다."
           - 과자: "새우깡, 허니버터칩, 포카칩이 잘 팔려요"
           - 커피: "아메리카노, 카페라떼, 카푸치노를 추천해요"

        예시 응답:
        - "허니버터칩을 추천드려요! 4.5점의 환상적인 맛으로 인기 상품이에요. 1,500원입니다."
        - "새우깡이 어떠세요? 바삭하고 맛있어서 고객들이 자주 찾는 상품이에요. 1,200원입니다."
        - "탄산음료라면 코카콜라나 펩시가 인기 있어요. 시원하고 달콤한 맛이에요."
        ${if (hasCustomerInfo) "- \"김안토니오님은 허니버터칩을 자주 찾으시네요! 허니버터칩 캐슬은 어떠세요? 기존보다 더 진한 맛으로 김안토니오님 취향에 딱 맞을 거예요. 2,500원입니다.\"" else ""}
        """.trimIndent()
    }

    // 정보 검색 전용 프롬프트
    private suspend fun createInformationSearchPrompt(hasImage: Boolean, hasCustomerInfo: Boolean): String {
        val products = if (hasImage) productDataService?.loadProducts() ?: emptyList() else emptyList()
        val productsInfo = if (hasImage) {
            products.joinToString("\n\n") { product ->
                val nutritionInfo = if (product.nutrition != null) {
                    """
                    영양성분 (${product.nutrition.servingSize}):
                    • 칼로리: ${product.nutrition.calories}kcal
                    • 단백질: ${product.nutrition.protein}g
                    • 지방: ${product.nutrition.fat}g
                    • 탄수화물: ${product.nutrition.carbohydrates}g
                    • 나트륨: ${product.nutrition.sodium}mg
                    • 당류: ${product.nutrition.sugar}g
                    """.trimIndent()
                } else {
                    ""
                }

                val eventsInfo = if (product.events.isNotEmpty()) {
                    """
                    이벤트/할인:
                    ${product.events.joinToString("\n") { "• ${it.description}${if (it.validUntil != null) " (유효기간: ${it.validUntil})" else ""}" }}" }}
                    """.trimIndent()
                } else {
                    ""
                }

                """
                상품명: ${product.name}
                브랜드: ${product.brand}
                카테고리: ${product.category}
                가격: ${product.price}원${if (product.originalPrice != null) " (원가: ${product.originalPrice}원)" else ""}
                설명: ${product.description}
                재고 상태: ${product.stockStatus}
                ${if (nutritionInfo.isNotEmpty()) nutritionInfo else ""}
                ${if (eventsInfo.isNotEmpty()) eventsInfo else ""}
                """.trimIndent()
            }
        } else {
            ""
        }

        return """
        당신은 편의점의 친절한 AI 직원입니다. 고객이 제품의 가격, 영양성분, 이벤트, 재고 등 구체적인 정보를 요청할 때 정확한 정보를 제공해주세요.

        ${if (hasImage) "다음은 현재 편의점에서 판매 중인 상품들의 상세 정보입니다:\n\n$productsInfo\n\n" else ""}

        응답 가이드라인:
        1. 요청한 정보를 정확하고 간결하게 제공
        2. 가격, 영양성분, 이벤트, 재고 상태 등 구체적인 수치 포함
        3. 음식이 아닌 경우 영양성분 정보는 제공하지 않음
        4. 70자 이내로 응답 (매우 중요!)
        5. 친절하고 정중한 톤 사용
        6. 매장 데이터에 없는 제품의 정보에 대해서도, 해당 제품의 일반적인 가격대, 영양성분 정보, 또는 관련 카테고리의 일반적인 특징을 알려드리며 질문에 대답할 수 있습니다.
           - 예시: "탄산음료는 보통 1,500원 정도이고, 당분이 많아요. 과자는 종류에 따라 칼로리가 다양하지만 일반적으로 높은 편입니다."
           - 제품 카테고리별 일반 정보 (예: "과자는 칼로리가 높아요")
        7. 정보를 찾을 수 없는 경우에도 친절하게 안내하고 대안 제시
        ${if (hasCustomerInfo) """8. 고객 정보(이름, 자주 구매한 상품, 구매 이력 요약 등)가 제공된 경우, 고객의 구매 이력이나 선호도에 대한 질문에 적극적으로 답변해주세요. 고객님이 본인의 정보를 요청하시는 것이므로, '개인정보'라는 이유로 안내를 거부하지 마세요. 안전하게 정보를 제공해야 합니다.
           - 예시: "김안토니오님, 고객님의 구매 내역을 확인해 드릴게요. 허니버터칩, 테리아 티크, 코카콜라제로 등을 자주 구매하셨습니다. 총 29개의 상품을 구매하셨어요."
           - 예시: "김안토니오님은 허니버터칩을 총 7개 구매하셨고, 최근 구매일은 2025년 7월 10일입니다."
           - 예시: "김안토니오님, 고객님의 자주 구매한 상품은 허니버터칩, 테리아 티크, 하리보 골드베렌입니다."
           - 예시: "네, 김안토니오님 고객님의 구매 내역은 안전하게 확인 가능합니다. 어떤 상품의 구매 내역을 알고 싶으신가요?"
        """ else """8. 고객 정보가 확인되지 않은 경우, '고객님의 정보가 확인되지 않아 구매 내역을 안내해 드리기 어렵습니다. 혹시 성함이나 전화번호를 알려주시면 확인해 드릴 수 있습니다.'와 같이 친절하게 안내해주세요. '개인정보'라는 단어를 직접적으로 사용하여 안내를 거부하는 표현은 사용하지 마세요."""}

        예시 응답:
        - "허니버터칩은 1,500원이고, 1회 제공량 기준 칼로리는 140kcal입니다."
        - "새우깡은 현재 1,200원에 판매 중이며, 재고가 충분합니다."
        - "탄산음료는 보통 1,500원 정도이고, 당분이 많아요."
        """.trimIndent()
    }

    override fun onCleared() {
        super.onCleared()
        ttsService?.release()
    }
}