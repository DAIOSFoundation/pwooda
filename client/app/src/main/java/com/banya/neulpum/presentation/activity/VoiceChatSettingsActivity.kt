package com.banya.neulpum.presentation.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.banya.neulpum.presentation.ui.screens.VoiceChatSettingsScreen

class VoiceChatSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        window.statusBarColor = android.graphics.Color.WHITE
        
        setContent {
            VoiceChatSettingsScreen(
                onBack = { finish() },
                onNavigateToVoiceDetectionSettings = {
                    val intent = Intent(this, VoiceDetectionSettingsActivity::class.java)
                    startActivity(intent)
                },
                onNavigateToVoiceSettings = {
                    val intent = Intent(this, VoiceSettingsActivity::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}

