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
}
