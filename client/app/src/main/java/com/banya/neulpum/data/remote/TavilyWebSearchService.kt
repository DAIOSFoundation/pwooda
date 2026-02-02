package com.banya.neulpum.data.remote

import com.banya.neulpum.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class TavilySearchResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double? = null
)

data class TavilySearchResponse(
    val query: String,
    val results: List<TavilySearchResult>
)

class TavilyWebSearchService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    private fun getTavilyApiKey(): String {
        return BuildConfig.TAVILY_API_KEY.ifEmpty {
            throw IllegalStateException("Tavily API 키가 설정되지 않았습니다. local.properties 파일에 TAVILY_API_KEY를 추가하세요.")
        }
    }
    
    /**
     * Tavily API를 사용하여 웹 검색 수행
     * @param query 검색 쿼리
     * @param maxResults 최대 결과 수 (기본값: 5)
     * @return 검색 결과 리스트
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5
    ): TavilySearchResponse = withContext(Dispatchers.IO) {
        try {
            val apiKey = getTavilyApiKey()
            
            val requestBody = JsonObject().apply {
                addProperty("api_key", apiKey)
                addProperty("query", query)
                addProperty("search_depth", "basic")
                addProperty("include_answer", true)
                addProperty("include_raw_content", false)
                addProperty("max_results", maxResults)
            }
            
            val mediaType = "application/json".toMediaType()
            val body = requestBody.toString().toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                throw Exception("Tavily API 오류: ${response.code} - ${responseBody}")
            }
            
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            
            val results = mutableListOf<TavilySearchResult>()
            val resultsArray = jsonResponse.getAsJsonArray("results")
            
            resultsArray?.forEach { element ->
                val resultObj = element.asJsonObject
                results.add(
                    TavilySearchResult(
                        title = resultObj.get("title")?.asString ?: "",
                        url = resultObj.get("url")?.asString ?: "",
                        content = resultObj.get("content")?.asString ?: "",
                        score = resultObj.get("score")?.asDouble
                    )
                )
            }
            
            val answer = jsonResponse.get("answer")?.asString
            
            TavilySearchResponse(
                query = query,
                results = results
            )
        } catch (e: Exception) {
            throw Exception("웹 검색 실패: ${e.message}", e)
        }
    }
    
    /**
     * 검색 결과를 텍스트 형식으로 포맷팅
     */
    fun formatSearchResults(response: TavilySearchResponse): String {
        if (response.results.isEmpty()) {
            return "검색 결과가 없습니다."
        }
        
        val sb = StringBuilder()
        sb.append("=== 웹 검색 결과 ===\n\n")
        
        response.results.forEachIndexed { index, result ->
            sb.append("[${index + 1}] ${result.title}\n")
            sb.append("URL: ${result.url}\n")
            sb.append("내용: ${result.content}\n\n")
        }
        
        return sb.toString()
    }
}

