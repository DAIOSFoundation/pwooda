package com.banya.neulpum.presentation.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.*

@Composable
fun SpeechRecognitionComponent(
    onSpeechResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
            if (granted) {
                startSpeechRecognition(context, onSpeechResult, onError)
                isListening = true
            }
        }
    )
    
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            isListening = false
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    val recognizedText = results[0].trim()
                    onSpeechResult(recognizedText)
                } else {
                    onError("음성을 인식할 수 없습니다")
                }
            } else {
                onError("음성 인식이 취소되었습니다")
            }
        }
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 음성 인식 상태 표시
        if (isListening) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF34A853).copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "음성 인식 중",
                        tint = Color(0xFF34A853),
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "음성 인식 중... 말씀해 주세요",
                        color = Color(0xFF34A853),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 음성 인식 버튼
        Button(
            onClick = {
                if (!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    startSpeechRecognition(context, onSpeechResult, onError)
                    isListening = true
                }
            },
            enabled = !isListening,
            modifier = Modifier
                .size(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) Color.Gray else Color(0xFF34A853)
            ),
            shape = RoundedCornerShape(40.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "음성 인식 중지" else "음성 인식 시작",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isListening) "음성 인식 중..." else "음성으로 질문하세요",
            color = if (isListening) Color(0xFF34A853) else Color.Gray,
            fontSize = 14.sp
        )
    }
}

private fun startSpeechRecognition(
    context: Context,
    onSpeechResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "질문을 말씀해 주세요")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // 음성 인식 대기 시간 설정
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }
        
        // 음성 인식이 지원되는지 확인
        if (intent.resolveActivity(context.packageManager) != null) {
            // ChatActivity에서 결과를 받기 위해 별도 처리 필요
            // 현재는 onSpeechResult를 직접 호출하여 시뮬레이션
            onSpeechResult("음성 인식이 시작되었습니다. 실제 구현에서는 음성 결과를 받습니다.")
        } else {
            onError("이 기기에서 음성 인식을 지원하지 않습니다")
        }
    } catch (e: Exception) {
        onError("음성 인식을 시작할 수 없습니다: ${e.message}")
    }
} 