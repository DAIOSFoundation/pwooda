package com.banya.pwooda.data

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Int,
    val originalPrice: Int? = null,
    val brand: String,
    val description: String,
    val features: List<String>,
    val ingredients: List<String>,
    val nutrition: NutritionInfo? = null,
    val taste: TasteInfo? = null,
    val reputation: ReputationInfo? = null,
    val events: List<Event>,
    val recommendations: List<String>,
    val imageResourceName: String? = null,
    val imagePath: String? = null,
    val stockStatus: String = "재고 있음"
)

data class NutritionInfo(
    val calories: Int,
    val protein: Double,
    val fat: Double,
    val carbohydrates: Double,
    val sodium: Int,
    val sugar: Double,
    val servingSize: String
)

data class Event(
    val type: String, // "할인", "증정", "이벤트"
    val description: String,
    val discountRate: Int? = null,
    val validUntil: String? = null
)

data class TasteInfo(
    val rating: Double, // 5점 만점
    val taste: String,
    val spiciness: Int, // 1-5 단계
    val popularity: String,
    val customerReviews: List<String>
)

data class ReputationInfo(
    val overallRating: Double, // 5점 만점
    val reviewCount: Int,
    val positiveReviews: Int,
    val negativeReviews: Int,
    val neutralReviews: Int,
    val reputationScore: String, // "매우 좋음", "좋음", "보통", "나쁨", "매우 나쁨"
    val marketPosition: String, // "시장 선도", "인기 상품", "평균 수준", "저조"
    val customerSatisfaction: String, // "매우 만족", "만족", "보통", "불만족", "매우 불만족"
    val weeklySales: Int, // 주간 판매량
    val salesRank: String, // "베스트셀러", "인기 상품", "평균 수준", "저조"
    val detailedReviews: List<String>,
    val pros: List<String>, // 장점
    val cons: List<String> // 단점
)