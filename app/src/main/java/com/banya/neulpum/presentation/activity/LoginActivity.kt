package com.banya.neulpum.presentation.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.banya.neulpum.data.repository.AuthRepositoryImpl
import com.banya.neulpum.presentation.ui.screens.LoginScreen
import com.banya.neulpum.presentation.viewmodel.AuthViewModel

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 상태바 설정 - 검은색 배경에 흰색 글씨
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // 흰색 글씨
        window.statusBarColor = android.graphics.Color.BLACK // 검은색 배경
        
        // If token exists, skip login UI and go straight to home
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null)
        if (!accessToken.isNullOrEmpty()) {
            goToChat()
            return
        }
        setContent {
            LoginContent { goToChat() }
        }
    }
    
    @Composable
    private fun LoginContent(onLoginSuccess: () -> Unit) {
        val authRepository = remember { AuthRepositoryImpl(this) }
        val authViewModel = remember { AuthViewModel(authRepository) }
        
        LoginScreen(
            authViewModel = authViewModel,
            onLoginSuccess = onLoginSuccess
        )
    }

    private fun goToChat() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun SimpleLoginScreen(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 로고
        Text(
            text = "늘품",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 부제목
        Text(
            text = "AI 스마트 매장 안내 시스템",
            fontSize = 18.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 48.dp)
        )
        
        // 간단한 설명
        Text(
            text = "로그인 후 AI 채팅을 시작하세요!",
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        // 로그인 버튼
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "로그인",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 게스트 로그인 버튼
        OutlinedButton(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "게스트로 시작하기",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
