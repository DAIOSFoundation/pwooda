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
            // 얼굴이 감지되면 상태를 확인하고 환영 메시지 출력
            val isAllowed = geminiViewModel?.isFaceDetectionAllowed() == true
            android.util.Log.d("FaceDetection", "얼굴 감지됨 - 얼굴 인식 허용 여부: $isAllowed")
            
            if (isAllowed) {
                // 클릭 사운드 재생
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
                
                // 얼굴 감지 시 고객을 "김안토니오"로 인식하도록 ViewModel에 설정
                geminiViewModel?.setRecognizedCustomer("김안토니오")
                speakWelcomeMessage()
            } else {
                val state = geminiViewModel?.state?.value
                android.util.Log.d("FaceDetection", "얼굴 인식 차단됨 - 상태: isLoading=${state?.isLoading}, isSpeaking=${state?.isSpeaking}, isVoiceDownloading=${state?.isVoiceDownloading}, isListening=${state?.isListening}, isCameraActive=${state?.isCameraActive}")
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
    
    override fun onDestroy() {
        super.onDestroy()
        faceDetectionService?.stopFaceDetection()
        ttsService?.release()
        clickSoundPlayer?.release() // MediaPlayer 해제
        clickSoundPlayer = null
        coroutineScope.cancel() // 코루틴 스코프 정리
    }
}