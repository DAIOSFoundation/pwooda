package com.banya.pwooda.data

data class Customer(
    val name: String,
    val phoneNumber: String,
    val purchaseHistory: List<PurchaseRecord> = emptyList(),
    val preferences: List<String> = emptyList()
)

data class PurchaseRecord(
    val productName: String,
    val purchaseDate: String,
    val quantity: Int = 1,
    val price: Int
)

data class CustomerRecommendation(
    val customerName: String,
    val phoneNumber: String,
    val frequentlyPurchased: List<String>,
    val recommendedProducts: List<String>,
    val recommendationReason: String
)