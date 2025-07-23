package com.banya.pwooda.service

import android.content.Context
import com.banya.pwooda.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductDataService(private val context: Context) {
    
    private val gson = Gson()
    
    suspend fun loadProducts(): List<Product> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.resources.openRawResource(
                context.resources.getIdentifier("products", "raw", context.packageName)
            ).bufferedReader().use { it.readText() }
            
            val productResponseType = object : TypeToken<ProductResponse>() {}.type
            val productResponse: ProductResponse = gson.fromJson(jsonString, productResponseType)
            
            productResponse.products
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun findProductByName(name: String): Product? {
        val products = loadProducts()
        return products.find { 
            it.name.contains(name, ignoreCase = true) || 
            it.brand.contains(name, ignoreCase = true) 
        }
    }
    
    suspend fun findProductsByCategory(category: String): List<Product> {
        val products = loadProducts()
        return products.filter { it.category.contains(category, ignoreCase = true) }
    }
    
    suspend fun getProductsWithEvents(): List<Product> {
        val products = loadProducts()
        return products.filter { it.events.isNotEmpty() }
    }
    
    data class ProductResponse(
        val products: List<Product>
    )
} 