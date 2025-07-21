package com.banya.pwooda.service

import android.content.Context
import com.banya.bgf_aistaff.data.Customer
import com.banya.bgf_aistaff.data.CustomerRecommendation
import com.banya.bgf_aistaff.data.PurchaseRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CustomerDataService(private val context: Context, private val productDataService: ProductDataService) {
    
    private val gson = Gson()
    
    // 고객 데이터 로드
    fun loadCustomers(): List<Customer> {
        return try {
            val inputStream = context.assets.open("customer.json") // customer.json으로 수정 (오타 수정)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            
            val jsonString = String(buffer)
            val type = object : TypeToken<List<Customer>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("CustomerDataService", "고객 데이터 로드 실패", e)
            emptyList()
        }
    }
    
    // 전화번호로 고객 찾기
    fun findCustomerByPhone(phoneNumber: String): Customer? {
        val customers = loadCustomers()
        return customers.find { it.phoneNumber == phoneNumber }
    }
    
    // 이름으로 고객 찾기
    fun findCustomerByName(name: String): Customer? {
        val customers = loadCustomers()
        return customers.find { it.name == name }
    }
    
    // 고객의 자주 구매한 상품 분석
    fun getFrequentlyPurchasedProducts(customer: Customer): List<String> {
        val productCount = mutableMapOf<String, Int>()
        
        customer.purchaseHistory.forEach { purchase ->
            val count = productCount.getOrDefault(purchase.productName, 0)
            productCount[purchase.productName] = count + purchase.quantity
        }
        
        return productCount.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }
    
    // 개인화 추천 생성
    suspend fun generatePersonalizedRecommendation(customer: Customer, question: String): CustomerRecommendation {
        val frequentlyPurchased = getFrequentlyPurchasedProducts(customer)
        val allProducts = productDataService.loadProducts()
        val productCategoryMap = allProducts.associate { it.name to it.category }
        
        val recommendedProducts = mutableListOf<String>()
        var recommendationReason = "${customer.name}님께 추천 상품을 안내해 드릴게요."

        val hasHoneyButterChipHistory = frequentlyPurchased.any { it.contains("허니버터칩", ignoreCase = true) }
        val hasTereaHistory = frequentlyPurchased.any { it.contains("테리아", ignoreCase = true) }
        val hasRamenHistory = frequentlyPurchased.any { product ->
            productCategoryMap[product]?.contains("라면/면류") == true
        }

        // 고객의 질문에 '과자' 키워드가 명시적으로 포함되어 있고, 과자 구매 이력이 있는 경우
        if (question.contains("과자", ignoreCase = true) && hasHoneyButterChipHistory) {
            val honeyButterChipCastle = allProducts.find { it.name == "허니버터칩 캐슬" }
            if (honeyButterChipCastle != null && !customer.purchaseHistory.any { it.productName == honeyButterChipCastle.name }) {
                recommendedProducts.add(honeyButterChipCastle.name)
                recommendationReason = "${customer.name}님은 허니버터칩을 자주 구매하시네요! 최근 구매하신 허니버터칩과 비슷한 허니버터칩 캐슬을 추천드려요."
            } else {
                // 이미 캐슬을 샀거나, 다른 과자 추천이 필요할 경우 (현재 데이터셋에 다른 과자 없음)
                if (honeyButterChipCastle != null) {
                    recommendedProducts.add(honeyButterChipCastle.name) // 이미 샀어도 일단 추천 목록에 포함
                    recommendationReason = "${customer.name}님은 과자를 즐겨 찾으시는군요! 허니버터칩 캐슬은 어떠세요?"
                }
            }
        }
        // 고객의 질문에 특정 카테고리 언급 없이, 단순 추천 요청일 경우 구매 이력 기반 추천
        else if (frequentlyPurchased.isNotEmpty()) {
            val topPurchasedProduct = frequentlyPurchased.first()
            val categoryOfTopProduct = productCategoryMap[topPurchasedProduct]

            when (categoryOfTopProduct) {
                "과자/스낵" -> {
                    val honeyButterChipCastle = allProducts.find { it.name == "허니버터칩 캐슬" }
                    if (topPurchasedProduct == "허니버터칩" && honeyButterChipCastle != null && !customer.purchaseHistory.any { it.productName == honeyButterChipCastle.name }) {
                        recommendedProducts.add(honeyButterChipCastle.name)
                        recommendationReason = "${customer.name}님은 허니버터칩을 자주 구매하시네요! 허니버터칩 캐슬도 좋아하실 거예요."
                    } else if (honeyButterChipCastle != null && !recommendedProducts.contains(honeyButterChipCastle.name)) {
                        recommendedProducts.add(honeyButterChipCastle.name)
                        recommendationReason = "${customer.name}님은 과자를 즐겨 찾으시는군요! 허니버터칩 캐슬은 어떠세요?"
                    } else if (!recommendedProducts.contains("허니버터칩")) {
                        recommendedProducts.add("허니버터칩")
                        recommendationReason = "${customer.name}님은 과자를 즐겨 찾으시는군요! 허니버터칩은 어떠세요?"
                    }
                }
                "전자담배" -> {
                    val ilumaProduct = allProducts.find { it.name == "일루마 훈연기" }
                    if (topPurchasedProduct == "테리아 티크" && ilumaProduct != null && !customer.purchaseHistory.any { it.productName == ilumaProduct.name }) {
                        recommendedProducts.add(ilumaProduct.name)
                        recommendationReason = "${customer.name}님은 테리아 티크를 자주 구매하시네요! 신제품 일루마 훈연기도 추천드려요."
                    } else if (ilumaProduct != null && !recommendedProducts.contains(ilumaProduct.name)) {
                        recommendedProducts.add(ilumaProduct.name)
                        recommendationReason = "${customer.name}님께 전자담배 일루마 훈연기를 추천드려요."
                    }
                }
                "라면/면류" -> {
                    if (topPurchasedProduct.contains("비빔면")) {
                        val otherBibim = allProducts.find { it.name.contains("비빔면") && it.name != topPurchasedProduct }
                        if (otherBibim != null && !customer.purchaseHistory.any { it.productName == otherBibim.name }) {
                            recommendedProducts.add(otherBibim.name)
                            recommendationReason = "${customer.name}님은 비빔면을 즐겨 드시는군요! ${otherBibim.name}도 추천드려요."
                        } else if (!recommendedProducts.contains("불닭볶음면")) {
                            recommendedProducts.add("불닭볶음면")
                            recommendationReason = "${customer.name}님은 라면을 즐겨 드시는군요! 불닭볶음면은 어떠세요?"
                        }
                    } else if (topPurchasedProduct == "불닭볶음면") {
                        val bibimNoodle = allProducts.find { it.name == "팔도 비빔면 컵" }
                        if (bibimNoodle != null && !customer.purchaseHistory.any { it.productName == bibimNoodle.name }) {
                            recommendedProducts.add(bibimNoodle.name)
                            recommendationReason = "${customer.name}님은 불닭볶음면을 즐겨 드시는군요! 팔도 비빔면 컵도 추천드려요."
                        }
                    }
                }
                // 기타 카테고리 또는 일반적인 경우
                else -> {
                    // 가장 잘 팔리는 상품 3개 추천 (스낵, 라면, 전자담배 등 다양하게)
                    val topSellers = allProducts.sortedByDescending { it.reputation?.weeklySales ?: 0 }.take(3).map { it.name }
                    recommendedProducts.addAll(topSellers.filter { !frequentlyPurchased.contains(it) }.take(3))
                    if (recommendedProducts.isNotEmpty()) {
                        recommendationReason = "${customer.name}님께 요즘 가장 잘 나가는 상품들을 추천드려요!"
                    }
                }
            }
        }
        
        // 추천 상품이 비어있을 경우 인기 과자를 기본으로 추가
        if (recommendedProducts.isEmpty()) {
            if (allProducts.any { it.category == "과자/스낵" }) {
                val popularSnack = allProducts.filter { it.category == "과자/스낵" }
                    .sortedByDescending { it.reputation?.overallRating ?: 0.0 }
                    .firstOrNull()?.name
                if (popularSnack != null) {
                    recommendedProducts.add(popularSnack)
                    recommendationReason = "${customer.name}님께 요즘 인기 있는 과자를 추천드려요!"
                }
            }
            if (recommendedProducts.isEmpty()) {
                 // 그래도 비어있으면 전체 상품 중 가장 판매량 높은 것 몇 개
                val topSellers = allProducts.sortedByDescending { it.reputation?.weeklySales ?: 0 }.take(3).map { it.name }
                recommendedProducts.addAll(topSellers.take(3))
                if (recommendedProducts.isNotEmpty()) {
                    recommendationReason = "${customer.name}님께 요즘 가장 잘 나가는 상품들을 추천드려요!"
                } else {
                    recommendationReason = "${customer.name}님께 추천해 드릴 상품이 아직 없네요."
                }
            }
        }

        // 최종적으로 추천 상품 목록에 중복 제거
        val finalRecommendedProducts = recommendedProducts.distinct().filter { it.isNotBlank() }
        
        return CustomerRecommendation(
            customerName = customer.name,
            phoneNumber = customer.phoneNumber,
            frequentlyPurchased = frequentlyPurchased,
            recommendedProducts = finalRecommendedProducts,
            recommendationReason = recommendationReason
        )
    }
    
    // 추천 관련 질문인지 확인 (현재 사용되지 않지만 유지)
    fun isRecommendationQuestion(question: String): Boolean {
        val recommendationKeywords = listOf(
            "추천", "추천해", "추천해주", "추천해줘", "추천해요", "추천해주세요",
            "뭐가", "뭘", "어떤", "좋은", "맛있는", "인기", "인기있는",
            "새로운", "신상", "신제품", "추천상품", "추천 제품"
        )
        
        return recommendationKeywords.any { keyword ->
            question.contains(keyword, ignoreCase = true)
        }
    }
}