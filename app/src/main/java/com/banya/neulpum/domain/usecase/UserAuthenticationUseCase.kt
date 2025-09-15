package com.banya.neulpum.domain.usecase

import com.banya.neulpum.domain.entity.User
import com.banya.neulpum.domain.repository.UserRepository
import javax.inject.Inject

class UserAuthenticationUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return userRepository.signInWithGoogle(idToken)
    }
    
    suspend fun signInWithApple(idToken: String): Result<User> {
        return userRepository.signInWithApple(idToken)
    }
    
    suspend fun signInWithKakao(accessToken: String): Result<User> {
        return userRepository.signInWithKakao(accessToken)
    }
    
    suspend fun signInAsGuest(): User {
        return userRepository.signInAsGuest()
    }
    
    suspend fun signOut() {
        userRepository.signOut()
    }
    
    suspend fun getCurrentUser(): User? {
        return userRepository.getCurrentUser()
    }
}
