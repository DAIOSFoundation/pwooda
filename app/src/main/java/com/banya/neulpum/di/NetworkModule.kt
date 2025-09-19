package com.banya.neulpum.di

import com.banya.neulpum.data.remote.AuthApiService
import com.banya.neulpum.data.remote.ChatApiService
import com.banya.neulpum.data.remote.OrganizationApiService
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Response
import android.content.Context
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.content.Intent
import com.banya.neulpum.presentation.activity.LoginActivity

object AppConfig {
    const val BASE_HOST = "https://api-llmops.banya.ai"
    // const val BASE_HOST = "http://192.168.0.3:8000"
    const val API_BASE_URL = "$BASE_HOST/api/v1/"
    const val APP_VERSION = "1.0.0"
}

object NetworkModule {
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        })
        .addInterceptor { chain ->
            val request = chain.request()
            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                // 네트워크 예외 시 세션 정리 후 로그인으로 이동
                val ctx = com.banya.neulpum.AppContextHolder.appContext
                if (ctx != null) {
                    try {
                        val prefs = ctx.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        val intent = Intent(ctx, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(intent)
                    } catch (_: Exception) {}
                }
                throw e
            }
            if (response.code == 401) {
                try {
                    // Attempt refresh
                    val ctx = com.banya.neulpum.AppContextHolder.appContext
                    if (ctx != null) {
                        val prefs = ctx.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        val refresh = prefs.getString("refresh_token", null)
                        if (!refresh.isNullOrEmpty()) {
                            val retrofitForAuth = Retrofit.Builder()
                                .baseUrl(AppConfig.API_BASE_URL)
                                .addConverterFactory(GsonConverterFactory.create())
                                .build()
                            val auth = retrofitForAuth.create(com.banya.neulpum.data.remote.AuthApiService::class.java)
                            val refreshResp = kotlinx.coroutines.runBlocking {
                                auth.refresh(com.banya.neulpum.data.remote.RefreshRequest(refresh))
                            }
                            if (refreshResp.isSuccessful) {
                                val body = refreshResp.body()?.data
                                if (body != null) {
                                    val newAccess = body.access_token
                                    val newRefresh = body.refresh_token ?: refresh
                                    prefs.edit().putString("access_token", newAccess).putString("refresh_token", newRefresh).apply()
                                    // retry original with new access if it used bearer
                                    val origAuth = request.header("Authorization")
                                    val newReq = if (origAuth != null && origAuth.startsWith("Bearer ")) {
                                        request.newBuilder().header("Authorization", "Bearer $newAccess").build()
                                    } else request
                                    response.close()
                                    return@addInterceptor chain.proceed(newReq)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                // 최종 실패 시 세션 정리 후 로그인 화면 이동
                val ctx = com.banya.neulpum.AppContextHolder.appContext
                if (ctx != null) {
                    try {
                        val prefs = ctx.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        val intent = Intent(ctx, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
            response
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val authApiService: AuthApiService = retrofit.create(AuthApiService::class.java)
    val chatApiService: ChatApiService = retrofit.create(ChatApiService::class.java)
    val organizationApiService: OrganizationApiService = retrofit.create(OrganizationApiService::class.java)
    
    fun provideOrganizationApiService(): OrganizationApiService = organizationApiService
}
