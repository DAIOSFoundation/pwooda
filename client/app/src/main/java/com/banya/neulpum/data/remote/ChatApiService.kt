package com.banya.neulpum.data.remote

import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import retrofit2.http.*

interface ChatApiService {
    
    @Streaming
    @GET("chat/sse")
    suspend fun chatSSE(
        @Query("message") message: String,
        @Query("provider_id") providerId: String? = null,
        @Header("X-API-Key") organizationApiKey: String? = null
    ): Response<okhttp3.ResponseBody>
}
