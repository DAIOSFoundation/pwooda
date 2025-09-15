package com.banya.neulpum.data.remote
import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {
    
    @POST("users/login")
    suspend fun login(
        @Header("Authorization") authorization: String
    ): Response<ApiResponse<LoginApiResponse>>
    
    @POST("users/register")
    suspend fun signup(
        @Body request: SignupApiRequest
    ): Response<ApiResponse<UserApiResponse>>
    
    @GET("users/my")
    suspend fun getMyProfile(
        @Header("Authorization") authorization: String
    ): Response<ApiResponse<UserApiResponse>>

    @POST("users/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): Response<ApiResponse<RefreshApiResponse>>
    
    @PUT("users/my")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: UserUpdateRequest
    ): Response<ApiResponse<UserApiResponse>>
    
    @DELETE("users/my")
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String,
        @Query("password") password: String
    ): Response<ApiResponse<Map<String, String>>>
    
    @POST("users/check-email")
    suspend fun checkEmail(
        @Body request: CheckEmailRequest
    ): Response<ApiResponse<CheckEmailResponse>>
    
    @POST("users/send-verification-email")
    suspend fun sendVerificationEmail(
        @Body request: SendVerificationEmailRequest
    ): Response<ApiResponse<SendVerificationEmailResponse>>
    
    @POST("users/verify-email")
    suspend fun verifyEmail(
        @Body request: VerifyEmailRequest
    ): Response<ApiResponse<VerifyEmailResponse>>
}

data class ApiResponse<T>(
    val data: T
)

data class LoginApiResponse(
    val access_token: String,
    val refresh_token: String,
    val user: UserApiResponse
)

data class UserApiResponse(
    val id: String,
    val email: String,
    val name: String,
    val created_at: String,
    val organization: OrganizationApiResponse?
)

data class OrganizationApiResponse(
    val id: String,
    val name: String,
    val description: String,
    val api_key: String,
    val owner_id: String,
    val is_active: Boolean,
    val created_at: String,
    val updated_at: String
)

data class RefreshApiResponse(
    val access_token: String,
    val refresh_token: String?
)

data class SignupApiRequest(
    val email: String,
    val name: String,
    val password: String
)

data class UserUpdateRequest(
    val name: String? = null,
    val current_password: String? = null,
    val new_password: String? = null
)

data class UserDeleteRequest(
    val password: String
)

data class CheckEmailRequest(
    val email: String
)

data class CheckEmailResponse(
    val available: Boolean,
    val message: String
)

data class SendVerificationEmailRequest(
    val email: String
)

data class SendVerificationEmailResponse(
    val message: String,
    val expires_in: Int
)

data class VerifyEmailRequest(
    val email: String,
    val verification_code: String
)

data class VerifyEmailResponse(
    val message: String,
    val verified: Boolean
)

data class RefreshRequest(
    val refresh_token: String
)
