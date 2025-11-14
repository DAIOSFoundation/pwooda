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
import com.banya.neulpum.utils.NetworkUtils

object AppConfig {
    const val BASE_HOST = "https://api-llmops.banya.ai"
    //const val BASE_HOST = "http://192.168.0.3:8000"
    const val API_BASE_URL = "$BASE_HOST/api/v1/"
    const val APP_VERSION = "1.0.1"
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
                // 네트워크 예외 처리
                val ctx = com.banya.neulpum.AppContextHolder.appContext
                if (ctx != null && NetworkUtils.isNetworkException(e)) {
                    // 네트워크가 연결되어 있지 않은 경우
                    if (!NetworkUtils.isNetworkAvailable(ctx)) {
                        // 토큰은 유지하고 네트워크 연결 확인 플래그만 설정
                        val prefs = ctx.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("show_network_dialog", true).apply()
                    } else {
                        // 네트워크는 연결되어 있지만 서버 연결 실패인 경우
                        // 토큰은 유지 (서버 문제일 수 있음)
                    }
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
                } catch (refreshException: Exception) {
                    // 토큰 갱신 실패 시 네트워크 상태 확인
                    val ctx = com.banya.neulpum.AppContextHolder.appContext
                    if (ctx != null) {
                        // 네트워크가 연결되어 있지 않으면 토큰 유지
                        if (!NetworkUtils.isNetworkAvailable(ctx) || NetworkUtils.isNetworkException(refreshException)) {
                            val prefs = ctx.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("show_network_dialog", true).apply()
                            // 토큰은 유지하고 네트워크 다이얼로그만 표시
                            return@addInterceptor response
                        }
                    }
                }
                // 네트워크는 연결되어 있고 토큰 갱신 실패 시에만 로그아웃 처리
                val ctx = com.banya.neulpum.AppContextHolder.appContext
                if (ctx != null) {
                    try {
                        // 네트워크가 연결되어 있는지 다시 확인
                        if (NetworkUtils.isNetworkAvailable(ctx)) {
                            // 실제 인증 실패인 경우에만 세션 정리
                        val prefs = ctx.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        val intent = Intent(ctx, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(intent)
                        } else {
                            // 네트워크가 없으면 토큰 유지
                            val prefs = ctx.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("show_network_dialog", true).apply()
                        }
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
