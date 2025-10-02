package com.banya.neulpum.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banya.neulpum.domain.entity.AuthResponse
import com.banya.neulpum.domain.entity.LoginRequest
import com.banya.neulpum.domain.entity.SignupRequest
import com.banya.neulpum.domain.entity.User
import com.banya.neulpum.domain.repository.AuthRepository
import com.banya.neulpum.domain.repository.AccountDeletionStatus
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    var authState by mutableStateOf<AuthState>(AuthState.Initial)
        private set
    
    var currentUser by mutableStateOf<User?>(null)
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var signupSuccessMessage by mutableStateOf<String?>(null)
        private set
    
    init {
        try {
            checkLoginStatus()
        } catch (e: Exception) {
            e.printStackTrace()
            authState = AuthState.Unauthenticated
        }
    }
    
    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            errorMessage = "이메일과 비밀번호를 입력해주세요."
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val response = authRepository.login(LoginRequest(email, password))
                
                if (response.success) {
                    response.user?.let { user ->
                        response.accessToken?.let { accessToken ->
                            authRepository.saveUserSession(user, accessToken, user.refreshToken ?: "", null)
                            currentUser = user
                            authState = AuthState.Authenticated(user)
                        }
                    }
                } else {
                    errorMessage = response.message
                    authState = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                errorMessage = "로그인 중 오류가 발생했습니다: ${e.message}"
                authState = AuthState.Unauthenticated
            } finally {
                isLoading = false
            }
        }
    }
    
    fun signup(email: String, password: String, name: String) {
        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            errorMessage = "모든 필드를 입력해주세요."
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val response = authRepository.signup(SignupRequest(email, password, name))
                
                if (response.success) {
                    // 회원가입 성공 시 자동 로그인 시도
                    try {
                        val loginResponse = authRepository.login(LoginRequest(email, password))
                        if (loginResponse.success) {
                            loginResponse.user?.let { user ->
                                loginResponse.accessToken?.let { accessToken ->
                                    authRepository.saveUserSession(user, accessToken, user.refreshToken ?: "", null)
                                    currentUser = user
                                    authState = AuthState.Authenticated(user)
                                    
                                    // 성공 메시지 설정
                                    signupSuccessMessage = response.message ?: "회원가입이 완료되었습니다!"
                                }
                            }
                        } else {
                            // 자동 로그인 실패 시 회원가입만 성공으로 처리
                            signupSuccessMessage = response.message ?: "회원가입이 완료되었습니다! 로그인해주세요."
                            authState = AuthState.Unauthenticated
                        }
                    } catch (e: Exception) {
                        // 자동 로그인 실패 시 회원가입만 성공으로 처리
                        signupSuccessMessage = response.message ?: "회원가입이 완료되었습니다! 로그인해주세요."
                        authState = AuthState.Unauthenticated
                    }
                } else {
                    errorMessage = response.message
                    authState = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                errorMessage = "회원가입 중 오류가 발생했습니다: ${e.message}"
                authState = AuthState.Unauthenticated
            } finally {
                isLoading = false
            }
        }
    }
    
    fun refreshToken() {
        viewModelScope.launch {
            try {
                val success = authRepository.refreshToken()
                if (success) {
                    // 토큰 갱신 성공 시 프로필 정보 업데이트
                    val updatedUser = authRepository.getMyProfile()
                    if (updatedUser != null) {
                        currentUser = updatedUser
                        authState = AuthState.Authenticated(updatedUser)
                    }
                } else {
                    // 토큰 갱신 실패 시 로그아웃
                    logout()
                }
            } catch (e: Exception) {
                errorMessage = "토큰 갱신 중 오류가 발생했습니다: ${e.message}"
                logout()
            }
        }
    }
    
    fun getMyProfile() {
        viewModelScope.launch {
            try {
                val user = authRepository.getMyProfile()
                if (user != null) {
                    currentUser = user
                    authState = AuthState.Authenticated(user)
                } else {
                    // 프로필 조회 실패 시 토큰 갱신 시도
                    refreshToken()
                }
            } catch (e: Exception) {
                errorMessage = "프로필 조회 중 오류가 발생했습니다: ${e.message}"
                // 프로필 조회 실패 시 토큰 갱신 시도
                refreshToken()
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.logout()
                currentUser = null
                authState = AuthState.Unauthenticated
            } catch (e: Exception) {
                errorMessage = "로그아웃 중 오류가 발생했습니다: ${e.message}"
            }
        }
    }
    
    fun checkLoginStatus() {
        viewModelScope.launch {
            try {
                val isLoggedIn = authRepository.isLoggedIn()
                if (isLoggedIn) {
                    // 토큰이 있으면 /my API를 호출해서 최신 사용자 정보 가져오기
                    val user = authRepository.getMyProfile()
                    if (user != null) {
                        currentUser = user
                        authState = AuthState.Authenticated(user)
                    } else {
                        // /my API 호출 실패 시 로그아웃
                        authState = AuthState.Unauthenticated
                    }
                } else {
                    authState = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                authState = AuthState.Unauthenticated
            }
        }
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    fun clearSignupSuccessMessage() {
        signupSuccessMessage = null
    }
    
    suspend fun updateProfile(name: String?, currentPassword: String?, newPassword: String?): Result<User> {
        return try {
            val result = authRepository.updateProfile(name, currentPassword, newPassword)
            if (result.isSuccess) {
                val updatedUser = result.getOrNull()
                if (updatedUser != null) {
                    currentUser = updatedUser
                    authState = AuthState.Authenticated(updatedUser)
                }
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteAccount(password: String): Result<Boolean> {
        return try {
            val result = authRepository.deleteAccount(password)
            if (result.isSuccess) {
                currentUser = null
                authState = AuthState.Unauthenticated
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun checkEmail(email: String): Result<Boolean> {
        return authRepository.checkEmail(email)
    }
    
    suspend fun sendVerificationEmail(email: String): Result<Boolean> {
        return authRepository.sendVerificationEmail(email)
    }
    
    suspend fun verifyEmail(email: String, verificationCode: String): Result<Boolean> {
        return authRepository.verifyEmail(email, verificationCode)
    }
    
    // 계정 삭제 요청 관련 메서드
    suspend fun requestAccountDeletion(email: String): Result<String> {
        return authRepository.requestAccountDeletion(email)
    }
    
    suspend fun verifyAccountDeletion(token: String): Result<String> {
        return authRepository.verifyAccountDeletion(token)
    }
    
    suspend fun getAccountDeletionStatus(token: String): Result<AccountDeletionStatus> {
        return authRepository.getAccountDeletionStatus(token)
    }
}

sealed class AuthState {
    object Initial : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
}
