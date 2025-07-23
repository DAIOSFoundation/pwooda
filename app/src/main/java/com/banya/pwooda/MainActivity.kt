package com.banya.pwooda

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banya.pwooda.ui.theme.PwoodaTheme
import com.banya.pwooda.ui.screens.MainScreen
import com.banya.pwooda.viewmodel.GeminiViewModel
import com.banya.pwooda.service.FaceDetectionService
import com.banya.pwooda.service.GoogleCloudTTSService
import com.banya.pwooda.util.WeatherGreetingUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {
    
    private var hasPermissions by mutableStateOf(false)
    private var faceDetectionService: FaceDetectionService? = null
    private var ttsService: GoogleCloudTTSService? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var lastWelcomeTime = 0L
    private var geminiViewModel: GeminiViewModel? = null

    // 얼굴 인식 시 재생할 클릭 사운드 MediaPlayer
    private var clickSoundPlayer: MediaPlayer? = null
    
    private var pendingFaceRecognition = false
    private var pendingNameInput = false
    private var recognizedName: String? = null
    // private var hasAskedName = false // 제거
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            initializeFaceDetection()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsService = GoogleCloudTTSService(this) // TTS 서비스 명시적 초기화
        
        checkAndRequestPermissions()

        // 클릭 사운드 MediaPlayer 초기화
        try {
            clickSoundPlayer = MediaPlayer.create(this, R.raw.click_sound)
            clickSoundPlayer?.setOnCompletionListener { mp ->
                mp.seekTo(0) // 재생 완료 시 처음으로 되감기 (재사용을 위해)
            }
            android.util.Log.d("MainActivity", "Click sound player initialized.")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize click sound player: ${e.message}", e)
        }
        
        setContent {
            PwoodaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: GeminiViewModel = viewModel()
                    geminiViewModel = viewModel
                    viewModel.setMainActivity(this) // MainActivity 참조 설정
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isEmpty()) {
            hasPermissions = true
            initializeFaceDetection()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }
    
    private fun initializeFaceDetection() {
        faceDetectionService = FaceDetectionService(this)
        faceDetectionService?.startFaceDetection(this) {
            val isAllowed = geminiViewModel?.isFaceDetectionAllowed() == true
            android.util.Log.d("FaceDetection", "얼굴 감지됨 - 얼굴 인식 허용 여부: $isAllowed")

            if (isAllowed && geminiViewModel?.hasAskedName?.value == false) {
                geminiViewModel?.setHasAskedName(true)
                try {
                    if (clickSoundPlayer?.isPlaying == true) {
                        clickSoundPlayer?.pause()
                        clickSoundPlayer?.seekTo(0)
                    }
                    clickSoundPlayer?.start()
                    android.util.Log.d("MainActivity", "Click sound played.")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to play click sound: ${e.message}", e)
                }
                // 얼굴 감지 시 다정한 환영 메시지 후 이름을 물어봄
                greetAndAskUserName()
            } else if (!isAllowed) {
                val state = geminiViewModel?.state?.value
                android.util.Log.d("FaceDetection", "얼굴 인식 차단됨 - 상태: isLoading=${state?.isLoading}, isSpeaking=${state?.isSpeaking}, isVoiceDownloading=${state?.isVoiceDownloading}, isListening=${state?.isListening}, isCameraActive=${state?.isCameraActive}")
            }
        }
    }

    private fun greetAndAskUserName() {
        coroutineScope.launch {
            ttsService?.synthesizeSpeech(
                text = "안녕! 만나서 너무 반가워. 나는 너의 AI 친구, 리나야! 너의 이름을 알려줄래? 예쁘게 말해줘!",
                onStart = {},
                onComplete = {
                    runOnUiThread { startNameSpeechRecognition() }
                },
                onError = {}
            )
        }
    }
    
    private fun askUserNameByVoice() {
        pendingNameInput = true
        // 이름을 물어보는 음성 안내 (10대 소녀 말투)
        coroutineScope.launch {
            ttsService?.synthesizeSpeech(
                text = "안녕! 혹시 네 이름이 뭐야? 예쁘게 말해줘!",
                onStart = {},
                onComplete = {
                    // 음성 안내가 끝나면 음성 인식 시작
                    runOnUiThread { startNameSpeechRecognition() }
                },
                onError = {}
            )
        }
    }

    private fun startNameSpeechRecognition() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "이름을 말씀해 주세요.")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        startActivityForResult(intent, 1001)
    }

    private fun findUserIdByRecognizedName(name: String): String? {
        val users = geminiViewModel?.customerDataServicePublic?.loadUsers() ?: return null
        // 1. 완전 일치
        users.find { it.nickname == name }?.id?.let { return it }
        // 2. 공백/대소문자 무시
        users.find { it.nickname.replace(" ", "").equals(name.replace(" ", ""), ignoreCase = true) }?.id?.let { return it }
        // 3. 자음/모음 분리 없이 포함만 해도 매칭
        users.find { name.replace(" ", "").contains(it.nickname.replace(" ", "")) }?.id?.let { return it }
        // 4. 초성/종성 무시(한글 자모 분리 등 추가 가능)
        // TODO: 필요시 한글 초성/종성 분리 라이브러리 활용
        return null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0].trim()
                
                // 이미 이름을 물어봤는지 확인
                if (geminiViewModel?.hasAskedName?.value == true) {
                    // 이름을 이미 물어봤다면 일반 질문으로 처리
                    android.util.Log.d("SpeechRecognition", "일반 질문으로 처리: $recognizedText")
                    coroutineScope.launch {
                        geminiViewModel?.askGemini(recognizedText)
                    }
                } else {
                    // 아직 이름을 물어보지 않았다면 이름 인식 시도
                    recognizedName = recognizedText
                    val userId = findUserIdByRecognizedName(recognizedName!!)
                    if (userId != null) {
                        geminiViewModel?.setHasAskedName(true)
                        geminiViewModel?.setRecognizedCustomerId(userId)
                        speakWelcomeMessage()
                    } else {
                        // 이름을 찾지 못한 경우 다시 시도
                        coroutineScope.launch {
                            ttsService?.synthesizeSpeech(
                                text = "앗! 등록된 이름이 아니래. 다시 한 번 또박또박 말해줄래?",
                                onStart = {},
                                onComplete = { runOnUiThread { startNameSpeechRecognition() } },
                                onError = {}
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun speakWelcomeMessage() {
        val currentTime = System.currentTimeMillis()
        
        // 마지막 환영 메시지 출력 후 20초 이내면 출력하지 않음 (TTS 중복 방지)
        if (currentTime - lastWelcomeTime < 20000) {
            android.util.Log.d("WelcomeTTS", "마지막 환영 메시지 출력 후 20초 이내, 출력 건너뜀")
            return
        }
        
        lastWelcomeTime = currentTime
        
        // GeminiViewModel에서 현재 인식된 고객 이름을 가져와서 환영 메시지 생성
        val recognizedCustomerName = geminiViewModel?.recognizedCustomerName?.value
        val welcomeMessage = WeatherGreetingUtil.getPersonalizedWelcomeMessage(recognizedCustomerName)
        
        android.util.Log.d("WelcomeTTS", "환영 메시지 TTS 시작: $welcomeMessage")
        
        // TTS 서비스 초기화
        if (ttsService == null) {
            ttsService = GoogleCloudTTSService(this)
        }
        
        // 코루틴 내에서 환영 메시지 TTS 출력
        coroutineScope.launch {
            ttsService?.synthesizeSpeech(
                text = welcomeMessage,
                onStart = {
                    android.util.Log.d("WelcomeTTS", "환영 메시지 TTS 시작됨")
                },
                onComplete = {
                    android.util.Log.d("WelcomeTTS", "환영 메시지 TTS 완료됨")
                    runOnUiThread { startNameSpeechRecognition() }
                },
                onError = { error ->
                    android.util.Log.e("WelcomeTTS", "환영 메시지 TTS 오류: $error")
                }
            )
        }
    }
    
    // 환영 메시지 TTS 중지 함수
    fun stopWelcomeTTS() {
        android.util.Log.d("WelcomeTTS", "환영 메시지 TTS 중지 요청")
        ttsService?.stop()
    }

    // 얼굴 감지 서비스에서 얼굴이 사라질 때 호출할 메서드에서 hasAskedName = false 제거
    fun onFaceLost() {
        // hasAskedName = false // 제거
    }
    
    override fun onDestroy() {
        super.onDestroy()
        faceDetectionService?.stopFaceDetection()
        ttsService?.release()
        clickSoundPlayer?.release() // MediaPlayer 해제
        clickSoundPlayer = null
        coroutineScope.cancel() // 코루틴 스코프 정리
    }
}