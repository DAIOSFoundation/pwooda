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
                    val viewModel: GeminiViewModel = viewModel { GeminiViewModel(this@MainActivity) }
                    geminiViewModel = viewModel
                    viewModel.setMainActivity(this) // MainActivity 참조 설정
                    viewModel.setHasAskedName(false) // 앱 시작 시 반드시 false로 초기화
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // 페이드 인 애니메이션 적용
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
            if (isAllowed && geminiViewModel?.hasAskedName?.value == false) {
                geminiViewModel?.setHasAskedName(true) // 중복 실행 방지: 여기서 true로 세팅
                try {
                    if (clickSoundPlayer?.isPlaying == true) {
                        clickSoundPlayer?.pause()
                        clickSoundPlayer?.seekTo(0)
                    }
                    clickSoundPlayer?.start()
                } catch (e: Exception) {}
                greetAndAskUserName()
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

    fun findUserIdByRecognizedName(name: String): String? {
        val users = geminiViewModel?.customerDataServicePublic?.loadUsers() ?: run {
            android.util.Log.e("MainActivity", "[로그] loadUsers() 결과가 null입니다.")
            return null
        }
        android.util.Log.d("MainActivity", "[로그] loadUsers() 반환: $users")
        if (users.isEmpty()) {
            android.util.Log.e("MainActivity", "[로그] loadUsers()가 빈 리스트입니다.")
        }
        // 1. 완전 일치
        users.find { it.nickname == name }?.id?.let {
            android.util.Log.d("MainActivity", "[로그] 닉네임 완전일치: $name -> id=$it")
            return it
        }
        // 2. 공백/대소문자 무시
        users.find { it.nickname.replace(" ", "").equals(name.replace(" ", ""), ignoreCase = true) }?.id?.let {
            android.util.Log.d("MainActivity", "[로그] 닉네임 공백/대소문자 무시 일치: $name -> id=$it")
            return it
        }
        // 3. 포함만 해도 매칭
        users.find { name.replace(" ", "").contains(it.nickname.replace(" ", "")) }?.id?.let {
            android.util.Log.d("MainActivity", "[로그] 닉네임 포함 매칭: $name -> id=$it")
            return it
        }
        android.util.Log.e("MainActivity", "[로그] 이름 매칭 실패: $name")
        return null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        android.util.Log.d("MainActivity", "[로그] onActivityResult 진입")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0].trim()
                android.util.Log.d("MainActivity", "[로그] onActivityResult - recognizedText: $recognizedText")
                android.util.Log.d("MainActivity", "[로그] onActivityResult - hasAskedName: ${geminiViewModel?.hasAskedName?.value}")
                // 이미 이름을 물어봤는지 확인
                if (geminiViewModel?.hasAskedName?.value == true) {
                    android.util.Log.d("SpeechRecognition", "일반 질문으로 처리: $recognizedText")
                    coroutineScope.launch {
                        geminiViewModel?.askGemini(recognizedText)
                    }
                } else {
                    recognizedName = recognizedText
                    val userId = findUserIdByRecognizedName(recognizedName!!)
                    android.util.Log.d("MainActivity", "[로그] 이름 인식 결과: $recognizedName, userId=$userId")
                    if (userId != null) {
                        geminiViewModel?.setRecognizedCustomerId(userId)
                        geminiViewModel?.setHasAskedName(true) // 반드시 userId 매칭 후에만 호출
                        speakWelcomeMessage()
                    } else {
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