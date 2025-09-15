package com.banya.neulpum.data.datasource

import com.banya.neulpum.data.remote.*
import com.banya.neulpum.di.NetworkModule
import retrofit2.Response

class AuthRemoteDataSource {
    private val api: AuthApiService = NetworkModule.authApiService

    suspend fun login(authorization: String): Response<ApiResponse<LoginApiResponse>> =
        api.login(authorization)

    suspend fun getMyProfile(authorization: String): Response<ApiResponse<UserApiResponse>> =
        api.getMyProfile(authorization)

    suspend fun refresh(refreshToken: String): Response<ApiResponse<RefreshApiResponse>> =
        api.refresh(RefreshRequest(refreshToken))

    suspend fun signup(request: SignupApiRequest): Response<ApiResponse<UserApiResponse>> =
        api.signup(request)
    
    suspend fun updateProfile(authorization: String, request: UserUpdateRequest): Response<ApiResponse<UserApiResponse>> =
        api.updateProfile(authorization, request)
    
    suspend fun deleteAccount(authorization: String, password: String): Response<ApiResponse<Map<String, String>>> =
        api.deleteAccount(authorization, password)
    
    suspend fun checkEmail(request: CheckEmailRequest): Response<ApiResponse<CheckEmailResponse>> =
        api.checkEmail(request)
    
    suspend fun sendVerificationEmail(request: SendVerificationEmailRequest): Response<ApiResponse<SendVerificationEmailResponse>> =
        api.sendVerificationEmail(request)
    
    suspend fun verifyEmail(request: VerifyEmailRequest): Response<ApiResponse<VerifyEmailResponse>> =
        api.verifyEmail(request)
}


