package com.banya.neulpum.domain.repository

import com.banya.neulpum.domain.entity.AuthResponse
import com.banya.neulpum.domain.entity.LoginRequest
import com.banya.neulpum.domain.entity.SignupRequest
import com.banya.neulpum.domain.entity.User

interface AuthRepository {
    suspend fun login(request: LoginRequest): AuthResponse
    suspend fun signup(request: SignupRequest): AuthResponse
    suspend fun refreshToken(): Boolean
    suspend fun getMyProfile(): User?
    suspend fun logout(): Boolean
    suspend fun getCurrentUser(): User?
    suspend fun isLoggedIn(): Boolean
    suspend fun saveUserSession(user: User, accessToken: String, refreshToken: String, organizationApiKey: String?): Boolean
    suspend fun clearUserSession(): Boolean
    suspend fun updateProfile(name: String?, currentPassword: String?, newPassword: String?): Result<User>
    suspend fun deleteAccount(password: String): Result<Boolean>
    suspend fun checkEmail(email: String): Result<Boolean>
    suspend fun sendVerificationEmail(email: String): Result<Boolean>
    suspend fun verifyEmail(email: String, verificationCode: String): Result<Boolean>
    
    // 계정 삭제 요청 관련 메서드
    suspend fun requestAccountDeletion(email: String): Result<String>
    suspend fun verifyAccountDeletion(token: String): Result<String>
    suspend fun getAccountDeletionStatus(token: String): Result<AccountDeletionStatus>
}

data class AccountDeletionStatus(
    val id: String,
    val email: String,
    val isVerified: Boolean,
    val createdAt: String,
    val verifiedAt: String?
)
