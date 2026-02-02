package com.banya.neulpum.presentation.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.banya.neulpum.presentation.ui.screens.TermsOfServiceScreen

class TermsOfServiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 상태바 설정 - 검은색 배경에 흰색 글씨
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // 흰색 글씨
        window.statusBarColor = android.graphics.Color.BLACK // 검은색 배경
        
        setContent {
            TermsOfServiceScreen(
                onBack = { finish() }
            )
        }
    }
}
