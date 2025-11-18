package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen() {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // 태블릿에서는 최대 너비 제한
    val maxContentWidth = when {
        screenWidth > 1200.dp -> 600.dp  // 큰 태블릿
        screenWidth > 800.dp -> 500.dp  // 중간 태블릿
        else -> screenWidth             // 핸드폰
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2E7D32),  // 농협 녹색
                        Color(0xFF1B5E20),  // 진한 녹색
                        Color(0xFF4CAF50)   // 밝은 녹색
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxContentWidth)
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
            text = "메인 스크린",
            fontSize = 18.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 48.dp)
        )
        
        // 간단한 설명
        Text(
            text = "메인 스크린이 성공적으로 로드되었습니다!",
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        // 기능 버튼들
        Button(
            onClick = { /* TODO: 검색 기능 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "검색",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Button(
            onClick = { /* TODO: 음성 인식 기능 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "음성 인식",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Button(
            onClick = { /* TODO: AI 채팅 기능 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "AI 채팅",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        }
    }
}