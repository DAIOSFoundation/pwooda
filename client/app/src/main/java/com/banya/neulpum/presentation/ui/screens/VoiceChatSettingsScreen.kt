package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banya.neulpum.presentation.ui.components.settings.SettingListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatSettingsScreen(
    onBack: () -> Unit,
    onNavigateToVoiceDetectionSettings: () -> Unit,
    onNavigateToVoiceSettings: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 상단 앱바
            TopAppBar(
                title = {
                    Text(
                    "음성채팅 설정",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        
        // 메인 콘텐츠
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 음성채팅 설정 메뉴 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    // 음성 감지 설정
                    SettingListRow(
                        title = "음성 감지 설정",
                        onClick = onNavigateToVoiceDetectionSettings,
                        showChevron = true
                    )
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                    
                    // 음성 설정
                    SettingListRow(
                        title = "음성 설정",
                        onClick = onNavigateToVoiceSettings,
                        showChevron = true
                    )
                }
            }
        }
    }
}

