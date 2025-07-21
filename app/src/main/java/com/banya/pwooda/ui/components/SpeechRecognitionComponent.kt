package com.banya.pwooda.ui.components

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.*

@Composable
fun SpeechRecognitionComponent(
    onSpeechResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                onSpeechResult(results[0])
            }
        } else {
            onError("음성인식에 실패했습니다.")
        }
        isListening = false
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                if (!isListening) {
                    startSpeechRecognition(context, speechLauncher)
                    isListening = true
                }
            },
            enabled = !isListening,
            modifier = Modifier.size(120.dp)
        ) {
            Text(if (isListening) "듣는 중..." else "음성 인식")
        }
        
        if (isListening) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "말씀해 주세요...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun startSpeechRecognition(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR") // 한국어 (대한민국)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR") // 언어 선호도 설정
        putExtra(RecognizerIntent.EXTRA_PROMPT, "질문을 말씀해 주세요. 말씀을 마치시면 잠시 기다려주세요.")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 부분 결과 허용
        
        // 음성 인식 대기 시간 관련 설정
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000) // 최소 음성 길이 2초
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000) // 완전 무음 6초 후 종료
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000) // 부분 무음 4초 후 종료
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000) // 가능한 완료 무음 3초 후 종료
    }
    
    launcher.launch(intent)
} 