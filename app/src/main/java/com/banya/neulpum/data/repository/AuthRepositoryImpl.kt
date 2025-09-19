package com.banya.neulpum.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.banya.neulpum.data.datasource.AuthRemoteDataSource
import com.banya.neulpum.data.remote.SignupApiRequest
import com.banya.neulpum.data.remote.UserUpdateRequest
import com.banya.neulpum.data.remote.CheckEmailRequest
import com.banya.neulpum.data.remote.SendVerificationEmailRequest
import com.banya.neulpum.data.remote.VerifyEmailRequest
import com.banya.neulpum.data.remote.AccountDeletionRequest
import com.banya.neulpum.data.remote.AccountDeletionVerifyRequest
import com.banya.neulpum.domain.entity.AuthResponse
import com.banya.neulpum.domain.entity.LoginRequest
import com.banya.neulpum.domain.entity.SignupRequest
import com.banya.neulpum.domain.entity.User
import com.banya.neulpum.domain.repository.AuthRepository
import com.banya.neulpum.domain.repository.AccountDeletionStatus
import kotlinx.coroutines.delay
import java.util.*

class AuthRepositoryImpl(
    private val context: Context
) : AuthRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val remote = AuthRemoteDataSource()
    
    override suspend fun login(request: LoginRequest): AuthResponse {
        try {
            // Basic Auth 헤더 생성 (Base64 encoded email:password)
            val credentials = "${request.email}:${request.password}"
            val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            val authorizationHeader = "Basic $encodedCredentials"
            
            // API 호출
            val response = remote.login(authorizationHeader)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                println("=== LOGIN API RESPONSE ===")
                println("Full API Response: $apiResponse")
                if (apiResponse != null && apiResponse.data != null) {
                    val loginData = apiResponse.data
                    println("Login Data: $loginData")
                    println("User Data: ${loginData.user}")
                    println("Organization Data: ${loginData.user.organization}")
                    println("Organization API Key: ${loginData.user.organization?.api_key}")
                    println("=========================")
                    val organizationApiKey = loginData.user.organization?.api_key
                    val organizationName = loginData.user.organization?.name
                    val organizationDescription = loginData.user.organization?.description
                    
                    val user = User(
                        id = loginData.user.id,
                        email = loginData.user.email,
                        name = loginData.user.name,
                        isLoggedIn = true,
                        accessToken = loginData.access_token,
                        refreshToken = loginData.refresh_token,
                        createdAt = loginData.user.created_at,
                        organizationName = organizationName,
                        organizationApiKey = organizationApiKey,
                        organizationDescription = organizationDescription
                    )
                    
                    // 세션 저장 (organization API key 포함)
                    println("Login Response - Organization: ${loginData.user.organization}")
                    println("Login Response - API Key: $organizationApiKey")
                    saveUserSession(user, loginData.access_token, loginData.refresh_token, organizationApiKey)
                    
                    return AuthResponse(
                        success = true,
                        message = "로그인에 성공했습니다!",
                        user = user,
                        accessToken = loginData.access_token
                    )
                } else {
                    return AuthResponse(
                        success = false,
                        message = "응답 데이터가 올바르지 않습니다."
                    )
                }
            } else {
                // 에러 응답 처리
                val errorMessage = when (response.code()) {
                    400 -> "이메일 또는 비밀번호가 올바르지 않습니다."
                    401 -> "인증에 실패했습니다."
                    500 -> "서버 오류가 발생했습니다."
                    else -> "로그인에 실패했습니다. (${response.code()})"
                }
                
                return AuthResponse(
                    success = false,
                    message = errorMessage
                )
            }
            
        } catch (e: Exception) {
            // 네트워크 오류 등
            return AuthResponse(
                success = false,
                message = "네트워크 오류: ${e.message}"
            )
        }
    }
    
    override suspend fun refreshToken(): Boolean {
        return try {
            val refreshToken = prefs.getString("refresh_token", null) ?: return false
            val response = remote.refresh(refreshToken)
            if (response.isSuccessful) {
                val apiResponse = response.body()
                val data = apiResponse?.data
                if (data != null) {
                    val newAccess = data.access_token
                    val newRefresh = data.refresh_token ?: refreshToken
                    prefs.edit().apply {
                        putString("access_token", newAccess)
                        putString("refresh_token", newRefresh)
                    }.apply()
                    true
                } else false
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getMyProfile(): User? {
        try {
            val accessToken = prefs.getString("access_token", null)
            if (accessToken.isNullOrEmpty()) {
                return null
            }
            
            val response = remote.getMyProfile("Bearer $accessToken")
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                println("=== /MY API RESPONSE ===")
                println("Full API Response: $apiResponse")
                if (apiResponse != null && apiResponse.data != null) {
                    val userData = apiResponse.data
                    println("User Data: $userData")
                    println("Organization Data: ${userData.organization}")
                    println("Organization API Key: ${userData.organization?.api_key}")
                    println("======================")
                    
                    // organization API key도 저장
                    val organizationApiKey = userData.organization?.api_key
                    val organizationName = userData.organization?.name
                    val organizationDescription = userData.organization?.description
                    if (organizationApiKey != null) {
                        prefs.edit().putString("organization_api_key", organizationApiKey).apply()
                        println("Saved Organization API Key: $organizationApiKey")
                    }
                    
                    return User(
                        id = userData.id,
                        email = userData.email,
                        name = userData.name,
                        isLoggedIn = true,
                        accessToken = accessToken,
                        refreshToken = prefs.getString("refresh_token", null),
                        profileUrl = null, // API에서 profile_url이 제공되지 않는 경우
                        createdAt = userData.created_at,
                        organizationName = organizationName,
                        organizationApiKey = organizationApiKey,
                        organizationDescription = organizationDescription
                    )
                }
            }
            
            return null
            
        } catch (e: Exception) {
            return null
        }
    }
    
    override suspend fun signup(request: SignupRequest): AuthResponse {
        try {
            // API 호출
            val response = remote.signup(
                SignupApiRequest(
                    email = request.email,
                    name = request.name,
                    password = request.password
                )
            )
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse != null && apiResponse.data != null) {
                    val userData = apiResponse.data
                    val user = User(
                        id = userData.id,
                        email = userData.email,
                        name = userData.name,
                        isLoggedIn = false, // 회원가입 후에는 자동 로그인되지 않음
                        accessToken = null,
                        refreshToken = null,
                        createdAt = userData.created_at
                    )
                    
                    return AuthResponse(
                        success = true,
                        message = "회원가입에 성공했습니다! 로그인해주세요.",
                        user = user
                    )
                } else {
                    return AuthResponse(
                        success = false,
                        message = "응답 데이터가 올바르지 않습니다."
                    )
                }
            } else {
                // 에러 응답 처리
                val errorMessage = when (response.code()) {
                    400 -> "입력 정보가 올바르지 않습니다."
                    409 -> "이미 존재하는 이메일입니다."
                    500 -> "서버 오류가 발생했습니다."
                    else -> "회원가입에 실패했습니다. (${response.code()})"
                }
                
                return AuthResponse(
                    success = false,
                    message = errorMessage
                )
            }
            
        } catch (e: Exception) {
            // 네트워크 오류 등
            return AuthResponse(
                success = false,
                message = "네트워크 오류: ${e.message}"
            )
        }
    }
    
    override suspend fun logout(): Boolean {
        return clearUserSession()
    }
    
    override suspend fun getCurrentUser(): User? {
        val userId = prefs.getString("user_id", null)
        val userEmail = prefs.getString("user_email", null)
        val userName = prefs.getString("user_name", null)
        val accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)
        
        return if (userId != null && userEmail != null && userName != null) {
            User(
                id = userId,
                email = userEmail,
                name = userName,
                isLoggedIn = true,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        } else null
    }
    
    override suspend fun isLoggedIn(): Boolean {
        val accessToken = prefs.getString("access_token", null)
        return !accessToken.isNullOrEmpty()
    }
    
    override suspend fun saveUserSession(user: User, accessToken: String, refreshToken: String, organizationApiKey: String?): Boolean {
        return try {
            prefs.edit().apply {
                putString("user_id", user.id)
                putString("user_email", user.email)
                putString("user_name", user.name)
                putString("access_token", accessToken)
                putString("refresh_token", refreshToken)
                putString("organization_api_key", organizationApiKey)
                putLong("login_time", System.currentTimeMillis())
            }.apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun clearUserSession(): Boolean {
        return try {
            prefs.edit().clear().apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun updateProfile(name: String?, currentPassword: String?, newPassword: String?): Result<User> {
        return try {
            val accessToken = prefs.getString("access_token", null)
            if (accessToken.isNullOrEmpty()) {
                return Result.failure(Exception("로그인이 필요합니다"))
            }
            
            val response = remote.updateProfile(
                "Bearer $accessToken",
                UserUpdateRequest(
                    name = name,
                    current_password = currentPassword,
                    new_password = newPassword
                )
            )
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse != null && apiResponse.data != null) {
                    val userData = apiResponse.data
                    val organizationApiKey = userData.organization?.api_key
                    val organizationName = userData.organization?.name
                    val organizationDescription = userData.organization?.description
                    
                    val user = User(
                        id = userData.id,
                        email = userData.email,
                        name = userData.name,
                        isLoggedIn = true,
                        accessToken = accessToken,
                        refreshToken = prefs.getString("refresh_token", null),
                        profileUrl = null,
                        createdAt = userData.created_at,
                        organizationName = organizationName,
                        organizationApiKey = organizationApiKey,
                        organizationDescription = organizationDescription
                    )
                    
                    // 세션 업데이트
                    saveUserSession(user, accessToken, prefs.getString("refresh_token", null) ?: "", organizationApiKey)
                    
                    Result.success(user)
                } else {
                    Result.failure(Exception("사용자 정보를 가져올 수 없습니다"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("프로필 업데이트 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteAccount(password: String): Result<Boolean> {
        return try {
            val accessToken = prefs.getString("access_token", null)
            if (accessToken.isNullOrEmpty()) {
                return Result.failure(Exception("로그인이 필요합니다"))
            }
            
            val response = remote.deleteAccount(
                "Bearer $accessToken",
                password
            )
            
            if (response.isSuccessful) {
                // 세션 정리
                clearUserSession()
                Result.success(true)
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("회원 탈퇴 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun checkEmail(email: String): Result<Boolean> {
        return try {
            val response = remote.checkEmail(CheckEmailRequest(email = email))
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.data?.available == true) {
                    Result.success(true) // 사용 가능
                } else {
                    Result.success(false) // 사용 불가능
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("이메일 확인 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendVerificationEmail(email: String): Result<Boolean> {
        return try {
            val response = remote.sendVerificationEmail(SendVerificationEmailRequest(email = email))
            
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("인증번호 발송 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun verifyEmail(email: String, verificationCode: String): Result<Boolean> {
        return try {
            val response = remote.verifyEmail(VerifyEmailRequest(email = email, verification_code = verificationCode))
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.data?.verified == true) {
                    Result.success(true)
                } else {
                    Result.success(false)
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("이메일 인증 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun requestAccountDeletion(email: String): Result<String> {
        return try {
            val response = remote.requestAccountDeletion(AccountDeletionRequest(email = email))
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                Result.success(apiResponse?.data?.message ?: "인증 이메일이 발송되었습니다.")
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("계정 삭제 요청 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun verifyAccountDeletion(token: String): Result<String> {
        return try {
            val response = remote.verifyAccountDeletion(AccountDeletionVerifyRequest(token = token))
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                Result.success(apiResponse?.data?.message ?: "본인 확인이 완료되었습니다.")
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("계정 삭제 인증 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAccountDeletionStatus(token: String): Result<AccountDeletionStatus> {
        return try {
            val response = remote.getAccountDeletionStatus(token)
            
            if (response.isSuccessful) {
                val apiResponse = response.body()
                val data = apiResponse?.data
                if (data != null) {
                    val status = AccountDeletionStatus(
                        id = data.id,
                        email = data.email,
                        isVerified = data.is_verified,
                        createdAt = data.created_at,
                        verifiedAt = data.verified_at
                    )
                    Result.success(status)
                } else {
                    Result.failure(Exception("상태 정보를 가져올 수 없습니다."))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                Result.failure(Exception("상태 확인 실패: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }
}
