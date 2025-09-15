package com.banya.neulpum.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class GeminiViewModel : ViewModel() {
    
    // 간단한 상태 변수들
    var isLoading by mutableStateOf(false)
    
    var response by mutableStateOf("")
    
    var error by mutableStateOf("")
    
    // 간단한 메서드들
    fun updateLoading(loading: Boolean) {
        isLoading = loading
    }
    
    fun updateResponse(text: String) {
        response = text
    }
    
    fun updateError(errorText: String) {
        error = errorText
    }
    
    fun clearError() {
        error = ""
    }
}
