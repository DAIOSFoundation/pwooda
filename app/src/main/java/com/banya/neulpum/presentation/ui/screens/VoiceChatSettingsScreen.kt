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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val prefs = remember { context.getSharedPreferences("voice_chat_prefs", android.content.Context.MODE_PRIVATE) }
    
    // 태블릿에서는 최대 너비 제한
    val maxContentWidth = when {
        screenWidth > 1200.dp -> 800.dp  // 큰 태블릿
        screenWidth > 800.dp -> 700.dp    // 중간 태블릿
        else -> screenWidth               // 핸드폰
    }
    
    // 초 단위로 관리 (불러올 때 밀리초를 초로 변환)
    var silenceDurationSeconds by remember { 
        mutableStateOf(prefs.getLong("silence_duration_ms", 4000L) / 1000.0)
    }
    // 음성 인식 종료 후 대기 시간 (초 단위)
    var endOfSpeechWaitSeconds by remember {
        mutableStateOf(prefs.getLong("end_of_speech_wait_ms", 3000L) / 1000.0)
    }
    var isSaving by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // 저장 함수 (초를 밀리초로 변환하여 저장)
    fun saveSettings() {
        isSaving = true
        try {
            val silenceMs = (silenceDurationSeconds * 1000).toLong()
            val endOfSpeechMs = (endOfSpeechWaitSeconds * 1000).toLong()
            android.util.Log.d("VoiceChatSettings", "저장 중: 무음=${silenceDurationSeconds}초, 종료대기=${endOfSpeechWaitSeconds}초")
            prefs.edit()
                .putLong("silence_duration_ms", silenceMs)
                .putLong("end_of_speech_wait_ms", endOfSpeechMs)
                .apply()
            // 저장 확인
            val savedSilence = prefs.getLong("silence_duration_ms", 4000L)
            val savedEndOfSpeech = prefs.getLong("end_of_speech_wait_ms", 3000L)
            android.util.Log.d("VoiceChatSettings", "저장 확인: 무음=${savedSilence}ms, 종료대기=${savedEndOfSpeech}ms")
            snackbarMessage = "저장되었습니다."
            showSnackbar = true
            scope.launch {
                kotlinx.coroutines.delay(500)
                onBack()
            }
        } catch (e: Exception) {
            snackbarMessage = "저장에 실패했습니다: ${e.message}"
            showSnackbar = true
            isSaving = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "음성채팅 설정",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
                    .padding(bottom = 80.dp), // 저장 버튼 공간 확보
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 무음 지속 시간 설정 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "무음 인식 시간",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "음성 입력 대기 무음 지속 시간입니다. (초)",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    
                    // 슬라이더와 텍스트 필드
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Slider(
                            value = silenceDurationSeconds.toFloat(),
                            onValueChange = { 
                                // 0.5초 단위로 반올림
                                silenceDurationSeconds = (it * 2).roundToInt() / 2.0
                            },
                            valueRange = 1.0f..10.0f,
                            steps = 18, // 0.5초 단위로 조정 가능 (1.0~10.0, 총 19단계)
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10A37F),
                                activeTrackColor = Color(0xFF10A37F),
                                inactiveTrackColor = Color.LightGray
                            )
                        )
                        OutlinedTextField(
                            value = String.format("%.1f", silenceDurationSeconds),
                            onValueChange = { 
                                val value = it.toDoubleOrNull()
                                if (value != null && value >= 1.0 && value <= 10.0) {
                                    // 0.5초 단위로 반올림
                                    silenceDurationSeconds = (value * 2).roundToInt() / 2.0
                                }
                            },
                            modifier = Modifier.width(100.dp),
                            suffix = { Text("초", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10A37F),
                                unfocusedBorderColor = Color.LightGray,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                
                }
                
                // 음성 인식 종료 후 대기 시간 설정 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "음성 대기 시간",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "추가 음성을 기다리는 시간입니다. (초)",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    
                    // 슬라이더와 텍스트 필드
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Slider(
                            value = endOfSpeechWaitSeconds.toFloat(),
                            onValueChange = { 
                                // 0.5초 단위로 반올림
                                endOfSpeechWaitSeconds = (it * 2).roundToInt() / 2.0
                            },
                            valueRange = 1.0f..10.0f,
                            steps = 18, // 0.5초 단위로 조정 가능 (1.0~10.0, 총 19단계)
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10A37F),
                                activeTrackColor = Color(0xFF10A37F),
                                inactiveTrackColor = Color.LightGray
                            )
                        )
                        OutlinedTextField(
                            value = String.format("%.1f", endOfSpeechWaitSeconds),
                            onValueChange = { 
                                val value = it.toDoubleOrNull()
                                if (value != null && value >= 1.0 && value <= 10.0) {
                                    // 0.5초 단위로 반올림
                                    endOfSpeechWaitSeconds = (value * 2).roundToInt() / 2.0
                                }
                            },
                            modifier = Modifier.width(100.dp),
                            suffix = { Text("초", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10A37F),
                                unfocusedBorderColor = Color.LightGray,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                
                }
            }
            
            // 저장 버튼 (하단 고정)
            Button(
                onClick = { saveSettings() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 15.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10A37F),
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "저장",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
    
    // Snackbar (하단에 표시)
    if (showSnackbar) {
        LaunchedEffect(showSnackbar) {
            kotlinx.coroutines.delay(3000)
            showSnackbar = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp),
                color = Color(0xFF10A37F),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        snackbarMessage, 
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

