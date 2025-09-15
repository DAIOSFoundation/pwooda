package com.banya.neulpum.domain.repository

import com.banya.neulpum.domain.entity.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signInWithApple(idToken: String): Result<User>
    suspend fun signInWithKakao(accessToken: String): Result<User>
    suspend fun signInAsGuest(): User
    suspend fun signOut()
    suspend fun deleteAccount()
}
