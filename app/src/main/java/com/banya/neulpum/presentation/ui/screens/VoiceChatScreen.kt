package com.banya.neulpum.presentation.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.banya.neulpum.data.remote.GoogleSpeechService
import com.banya.neulpum.presentation.ui.components.EqualizerBars
import com.banya.neulpum.presentation.ui.components.EqualizerCenterReactive
import com.banya.neulpum.data.remote.VoiceWebSocketClient
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.banya.neulpum.presentation.ui.components.voice.VoiceCenterOverlay
import com.banya.neulpum.presentation.ui.components.voice.VoiceMicButton
import com.banya.neulpum.presentation.ui.components.voice.CircularParticleView
import com.banya.neulpum.utils.PermissionHelper
import com.banya.neulpum.utils.rememberPermissionHelper
import com.banya.neulpum.presentation.ui.components.MicrophonePermissionDialog
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.random.Random
import android.media.audiofx.Visualizer
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    conversationId: String? = null,
    onConversationCreated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val permissionHelper = rememberPermissionHelper()
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    var partialText by remember { mutableStateOf("") }
    var speechService by remember { mutableStateOf<GoogleSpeechService?>(null) }
    
    var hasMicrophonePermission by remember {
        mutableStateOf(
            permissionHelper.isPermissionGranted(PermissionHelper.RECORD_AUDIO_PERMISSION)
        )
    }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // 화면이 표시될 때마다 권한 상태를 다시 확인
    LaunchedEffect(Unit) {
        hasMicrophonePermission = permissionHelper.isPermissionGranted(PermissionHelper.RECORD_AUDIO_PERMISSION)
    }
    
    
    var wsClient by remember { mutableStateOf<VoiceWebSocketClient?>(null) }
    var isWsConnected by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isAwaitingResponse by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableStateOf(0f) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var doneReceived by remember { mutableStateOf(false) }
    var fallbackScheduled by remember { mutableStateOf(false) }
    var lastAudioFormat by remember { mutableStateOf<String?>(null) }
    var isWsConnecting by remember { mutableStateOf(false) }
    val mainHandler = remember { androidx.core.os.HandlerCompat.createAsync(android.os.Looper.getMainLooper()) }
    var lastRecognizedText by remember { mutableStateOf<String?>(null) }
    var currentConversationId by remember { mutableStateOf(conversationId) }
    
    
    // 녹음 시간 업데이트
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingTime++
            }
        } else {
            recordingTime = 0
        }
    }
    
    // 음성 인식 서비스 및 WebSocket 초기화
    LaunchedEffect(Unit) {
        speechService = GoogleSpeechService(context, "YOUR_GOOGLE_API_KEY")
        // WebSocket 연결 설정
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val access = prefs.getString("access_token", null)
        val orgKey = prefs.getString("organization_api_key", null)
        val base = com.banya.neulpum.di.AppConfig.BASE_HOST
        val wsScheme = if (base.startsWith("https")) "wss" else "ws"
        val wsUrl = base.replaceFirst(Regex("^https?"), wsScheme) + "/ws/voice"
        // mainHandler 및 debugToast는 Composable 스코프에서 remember로 공유됨
        player = ExoPlayer.Builder(context).build().apply {
            val attrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
            setAudioAttributes(attrs, true)
            volume = 1f
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        mainHandler.post {
                            isPlaying = false
                            audioLevel = 0f
                            try { visualizer?.release() } catch (_: Exception) {}
                            visualizer = null
                        }
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    mainHandler.post {
                        isPlaying = false
                        audioLevel = 0f
                        try { visualizer?.release() } catch (_: Exception) {}
                        visualizer = null
                    }
                }
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    mainHandler.post { isPlaying = isPlayingNow }
                }
            })
        }
        wsClient = VoiceWebSocketClient(wsUrl, access, orgKey, object: VoiceWebSocketClient.Listener {
            override fun onOpen() {
                isWsConnected = true
                isWsConnecting = false
                // 연결 지연 시 보낸 텍스트를 즉시 전송
                val p = pendingText
                if (!p.isNullOrBlank()) {
                    wsClient?.sendText(p, currentConversationId)
                    pendingText = null
                }
                
            }
            override fun onTtsChunk(bytes: ByteArray, format: String?) {
                // 기존 방식 (호환성을 위해 유지) - 버퍼링 없이 즉시 재생
                GlobalScope.launch {
                    try {
                        lastAudioFormat = format
                        
                        if (bytes.isNotEmpty()) {
                            val ext = if (format == "wav") ".wav" else ".mp3"
                            val cache = java.io.File.createTempFile("tts_", ext, context.cacheDir)
                            cache.writeBytes(bytes)
                            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(cache))
                            mainHandler.post {
                                player?.stop()
                                player?.clearMediaItems()
                                player?.setMediaItem(mediaItem)
                                player?.prepare()
                                player?.play()
                                isPlaying = true
                                isAwaitingResponse = false
                                doneReceived = true
                                fallbackScheduled = false
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            override fun onTtsChunkStream(bytes: ByteArray, format: String?, chunkIndex: Int, isFinal: Boolean) {
                // 스트리밍 오디오 청크 처리 - 즉시 재생
                GlobalScope.launch {
                    try {
                        lastAudioFormat = format
                        
                        if (bytes.isNotEmpty()) {
                            val ext = if (format == "wav") ".wav" else ".mp3"
                            val tempFile = java.io.File.createTempFile("tts_stream_${chunkIndex}_", ext, context.cacheDir)
                            tempFile.writeBytes(bytes)
                            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(tempFile))
                            
                            mainHandler.post {
                                if (chunkIndex == 0) {
                                    // 첫 청크는 즉시 재생 시작
                                    player?.stop()
                                    player?.clearMediaItems()
                                    player?.setMediaItem(mediaItem)
                                    player?.prepare()
                                    player?.play()
                                    isPlaying = true
                                    isAwaitingResponse = false
                                } else {
                                    // 후속 청크는 큐에 추가
                                    player?.addMediaItem(mediaItem)
                                }
                                
                                // 최종 청크인 경우 상태 정리
                                if (isFinal) {
                                    doneReceived = true
                                    fallbackScheduled = false
                                    isAwaitingResponse = false
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        println("VoiceChatScreen: Error in onTtsChunkStream: $e")
                    }
                }
            }
            override fun onDone() {
                // onDone은 이제 상태 초기화만 담당 (재생은 onTtsChunk에서 처리)
                doneReceived = true
                fallbackScheduled = false
                isAwaitingResponse = false
            }
            override fun onError(error: String) {
                isAwaitingResponse = false
                isWsConnecting = false
            }
            override fun onLog(stage: String, message: String) {
                // 서버 로그는 필요시에만 처리
            }
            override fun onClose(code: Int, reason: String) {
                isWsConnected = false
                isAwaitingResponse = false
                isWsConnecting = false
                
            }
            override fun onConversationCreated(conversationId: String) {
                currentConversationId = conversationId
                onConversationCreated(conversationId)
            }
        })
        // 연결은 즉시 하지 않음. 마이크 버튼을 눌렀을 때 필요 시 연결.
    }

    DisposableEffect(Unit) {
        onDispose {
            try { wsClient?.close() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 상단 화려한 파티클 이퀄라이저 - 상단에서 여백 주고 조금 내려서 배치
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            // 상단에서 여백을 주고 조금 내려서 배치
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
            // 화려한 파티클 이퀄라이저
            AndroidView(
                factory = { ctx ->
                    CircularParticleView(ctx).apply { 
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        startVisualizing() 
                    }
                },
                update = { view ->
                    if (isRecording || isPlaying) {
                        view.startVisualizing()
                        // 최적화된 FFT 데이터 생성 - 캐시된 계산 사용
                        val fftData = ByteArray(64) { i ->
                            val baseLevel = (audioLevel * 60).toInt()
                            val wave = (kotlin.math.sin(i * 0.3) * 20).toInt()
                            (baseLevel + wave).coerceIn(0, 127).toByte()
                        }
                        view.setFftData(fftData)
                    } else {
                        view.startVisualizing()
                        // 대기 상태 - 간단한 애니메이션
                        val fftData = ByteArray(64) { i ->
                            val idle = (kotlin.math.sin(i * 0.2) * 10 + 20).toInt()
                            idle.coerceIn(0, 127).toByte()
                        }
                        view.setFftData(fftData)
                    }
                },
                modifier = Modifier
                    .size(300.dp)
                    .background(Color.Transparent)
            )
            }
        }
        // 하단 영역(패딩 포함) UI - 배경 제거하여 중앙 오버레이가 보이도록
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 전송(녹음) 중일 때 버튼 위 작은 이퀄라이저
            if (isRecording) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EqualizerBars(active = true, barColor = Color(0xFF10A37F))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 음성 녹음 FAB (하단 중앙)
            VoiceMicButton(
                isRecording = isRecording,
                paddingValues = paddingValues,
                onToggle = {
                if (isRecording) {
                    isRecording = false
                    speechService?.cleanup()
                } else {
                    if (hasMicrophonePermission) {
                        // 소켓이 미연결 상태이면 우선 연결 시도
                        if (!isWsConnected && !isWsConnecting) {
                            isWsConnecting = true
                            try { wsClient?.connect() } catch (_: Exception) { isWsConnecting = false }
                        }
                        // 새 녹음 시작 시 기존 최종 텍스트는 숨김
                        lastRecognizedText = null
                        isRecording = true
                        partialText = ""
                        // 보이스 요약 텍스트 초기화 제거
                        speechService?.startSpeechRecognition(
                            onPartialResult = { text ->
                                partialText = text
                            },
                            onFinalResult = { text ->
                                isRecording = false
                                partialText = ""
                                lastRecognizedText = text
                                if (text.isNotEmpty()) {
                                    // 새 요청 시작 시 이전 재생 상태 완전 초기화
                                    try { player?.stop() } catch (_: Exception) {}
                                    try { player?.clearMediaItems() } catch (_: Exception) {}
                                    isPlaying = false
                                    audioLevel = 0f
                                    try { visualizer?.release() } catch (_: Exception) {}
                                    visualizer = null
                                    // 상태 변수들 완전 초기화
                                    doneReceived = false
                                    fallbackScheduled = false
                                    isAwaitingResponse = true
                                    if (isWsConnected) {
                                        wsClient?.sendText(text, currentConversationId)
                                    } else {
                                        pendingText = text
                                    }
                                }
                            },
                            onError = { _ ->
                                isRecording = false
                                partialText = ""
                                isAwaitingResponse = false
                            }
                        )
                    } else {
                        // 마이크 권한이 없는 경우 권한 요청 다이얼로그 표시
                        showPermissionDialog = true
                    }
                }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )

            // 마지막 음성 인식 결과 텍스트 (하단 고정 표시)
            if (!lastRecognizedText.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF7F7F8)
                    ) {
                        Text(
                            text = lastRecognizedText ?: "",
                            color = Color.Black,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }

    // 오디오 레벨 시각화: 최적화된 Visualizer 사용
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 세션 ID 대기 시간 단축
            repeat(5) {
                if (player?.audioSessionId != C.AUDIO_SESSION_ID_UNSET) return@repeat
                delay(20)
            }
            val sessionId = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                try {
                    visualizer = Visualizer(sessionId).apply {
                        captureSize = Visualizer.getCaptureSizeRange()[0] // 최소 크기 사용
                        setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, bytes: ByteArray?, samplingRate: Int) {
                                if (bytes == null) return
                                // 최적화된 RMS 계산
                                var sum = 0f
                                val len = bytes.size
                                for (i in 0 until len step 4) { // 4배 샘플링으로 성능 향상
                                    val b = bytes[i]
                                    val fb = (b.toInt() and 0xFF) - 128
                                    sum += (fb * fb)
                                }
                                val rms = sqrt(sum / (len / 4)) / 128f
                                audioLevel = rms.coerceIn(0f, 1f)
                            }
                            override fun onFftDataCapture(v: Visualizer?, bytes: ByteArray?, samplingRate: Int) { }
                        }, Visualizer.getMaxCaptureRate() / 4, true, false) // 캡처 레이트 감소
                        enabled = true
                    }
                } catch (_: Exception) {
                    // 폴백 애니메이션 간소화
                    while (isPlaying) {
                        audioLevel = 0.4f + Random.nextFloat() * 0.4f
                        delay(100) // 업데이트 간격 증가
                    }
                    audioLevel = 0f
                }
            } else {
                // 폴백 애니메이션 간소화
                while (isPlaying) {
                    audioLevel = 0.4f + Random.nextFloat() * 0.4f
                    delay(100) // 업데이트 간격 증가
                }
                audioLevel = 0f
            }
        } else {
            audioLevel = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { visualizer?.release() } catch (_: Exception) {}
            visualizer = null
        }
    }
    
    // 마이크 권한 요청 다이얼로그
    if (showPermissionDialog) {
        MicrophonePermissionDialog(
            onConfirm = {
                showPermissionDialog = false
                permissionHelper.openAppSettings()
            },
            onDismiss = {
                showPermissionDialog = false
            }
        )
    }
    
}

