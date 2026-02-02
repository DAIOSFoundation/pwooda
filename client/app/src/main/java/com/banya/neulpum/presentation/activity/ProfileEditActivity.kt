package com.banya.neulpum.presentation.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.banya.neulpum.data.repository.AuthRepositoryImpl
import com.banya.neulpum.presentation.ui.screens.ProfileEditScreen
import com.banya.neulpum.presentation.viewmodel.AuthViewModel

class ProfileEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        window.statusBarColor = android.graphics.Color.WHITE
        
        val authRepository = AuthRepositoryImpl(this)
        val authViewModel = AuthViewModel(authRepository)
        
        setContent {
            ProfileEditScreen(
                onBack = { finish() },
                authViewModel = authViewModel
            )
        }
    }
}

