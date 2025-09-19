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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
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
    
        // 워크플로우 상태
        var currentWorkflowStep by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }
    
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
    // 음성 채팅도 일반 채팅과 동일하게 conversationId 사용
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
                    wsClient?.sendText(p, currentConversationId) // 기존 대화 사용 (일반 채팅과 동일)
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
                    println("VoiceChatScreen: onDone called")
                    android.util.Log.d("VoiceChatScreen", "onDone called")
                    // onDone은 이제 상태 초기화만 담당 (재생은 onTtsChunk에서 처리)
                    doneReceived = true
                    fallbackScheduled = false
                    isAwaitingResponse = false
                    
                    // 워크플로우 완료 상태로 업데이트
                    currentWorkflowStep = ""
                    isProcessing = false
                    println("VoiceChatScreen: Workflow completed - step cleared, processing=false")
                    android.util.Log.d("VoiceChatScreen", "Workflow completed - step cleared, processing=false")
                    
                    // 보이스 채팅 완료 시 대화 생성 콜백 호출 (대화가 생성되었다면)
                    if (currentConversationId != null) {
                        onConversationCreated(currentConversationId!!)
                    }
                }
            override fun onError(error: String) {
                isAwaitingResponse = false
                isWsConnecting = false
            }
            override fun onLog(stage: String, message: String) {
                // 워크플로우 단계 업데이트 - 더 알아보기 쉽게
                when (stage) {
                    "plan", "planner" -> {
                        currentWorkflowStep = "🤔 생각 중..."
                        isProcessing = true
                    }
                    "tool_executor" -> {
                        currentWorkflowStep = "🔧 작업 중..."
                        isProcessing = true
                    }
                    "summarize" -> {
                        currentWorkflowStep = "✍️ 답변 작성 중..."
                        isProcessing = true
                    }
                    "tts" -> {
                        currentWorkflowStep = "🎵 음성 변환 중..."
                        isProcessing = true
                    }
                    "done" -> {
                        currentWorkflowStep = ""
                        isProcessing = false
                    }
                    else -> {
                        currentWorkflowStep = when (stage) {
                            "rag" -> "📚 정보 검색 중..."
                            "mcp" -> "🔗 도구 실행 중..."
                            "api" -> "🌐 API 호출 중..."
                            "db" -> "💾 데이터 처리 중..."
                            else -> "⚙️ 처리 중..."
                        }
                        isProcessing = true
                    }
                }
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
        // 오디오 비주얼라이저 - 고정 위치 (화면 중앙 위쪽)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // 고정된 중앙 위쪽 위치
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-40).dp), // 40dp 위로 이동
                contentAlignment = Alignment.Center
            ) {
                // 예쁜 원형 오디오 비주얼라이저
                BeautifulCircularVisualizer(
                    isActive = isRecording || isPlaying,
                    audioLevel = audioLevel,
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        
        // 워크플로우 표시기 - 햄버거바+제목에서 15dp 아래 (예쁘게 꾸미기)
        if (currentWorkflowStep.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .offset(y = 15.dp), // 햄버거바+제목에서 15dp 아래
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 그라데이션 원형 로딩
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF10A37F).copy(alpha = 0.8f),
                                        Color(0xFF10A37F).copy(alpha = 0.3f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = currentWorkflowStep,
                        color = Color(0xFF10A37F),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
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
                        
                        // 보이스 채팅 시작 시에는 새로운 대화를 생성하지 않음
                        // 서버에서 conversation_created 이벤트를 받을 때만 onConversationCreated 호출
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
                                        wsClient?.sendText(text, currentConversationId) // 기존 대화 사용 (일반 채팅과 동일)
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

@Composable
fun BeautifulCircularVisualizer(
    isActive: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beautiful_visualizer")
    
    // 회전 애니메이션
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // 펄스 애니메이션
    // val pulse by infiniteTransition.animateFloat(
    //     initialValue = 0.9f,
    //     targetValue = 1.1f,
    //     animationSpec = infiniteRepeatable(
    //         animation = tween(3000, easing = EaseInOut),
    //         repeatMode = RepeatMode.Reverse
    //     ),
    //     label = "pulse"
    // )
    
    // 색상 그라데이션 애니메이션
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color_shift"
    )
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) / 2 * 0.85f
        
        // 그라데이션 배경 원
        val gradientColors = listOf(
            Color(0xFF10A37F).copy(alpha = 0.1f),
            Color(0xFF00D4AA).copy(alpha = 0.05f),
            Color(0xFF10A37F).copy(alpha = 0.1f)
        )
        
        // drawCircle(
        //     brush = Brush.radialGradient(
        //         colors = gradientColors,
        //         center = center,
        //         radius = radius * pulse
        //     ),
        //     radius = radius * pulse,
        //     center = center
        // )
        
        if (isActive) {
            // 활성 상태 - 더 예쁜 바들
            val barCount = 48
            val barWidth = 3.dp.toPx()
            
            rotate(rotation, center) {
                for (i in 0 until barCount) {
                    val angle = (i * 360f / barCount) * (Math.PI / 180f).toFloat()
                    val baseHeight = if (audioLevel > 0) {
                        (audioLevel * 80f + sin(i * 0.4f) * 30f).coerceIn(15f, 100f)
                    } else {
                        sin(i * 0.3f) * 15f + 25f
                    }
                    
                    val barHeight = baseHeight
                    val x = center.x + cos(angle) * (radius - barHeight / 2)
                    val y = center.y + sin(angle) * (radius - barHeight / 2)
                    
                    // 그라데이션 색상
                    val hue = (colorShift + i * 0.1f) % 1f
                    val barColor = when {
                        barHeight > 80f -> Color.hsl(hue * 360f, 0.7f, 0.5f)
                        barHeight > 60f -> Color.hsl(hue * 360f, 0.6f, 0.6f)
                        barHeight > 40f -> Color.hsl(hue * 360f, 0.5f, 0.7f)
                        else -> Color.hsl(hue * 360f, 0.4f, 0.8f)
                    }
                    
                    // 둥근 모서리 바
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x - barWidth / 2, y - barHeight / 2),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                    )
                }
            }
            
        
        } else {
            // 대기 상태 - 더 예쁜 애니메이션
            val dotCount = 16
            for (i in 0 until dotCount) {
                val angle = (i * 360f / dotCount) * (Math.PI / 180f).toFloat()
                val dotRadius = 6.dp.toPx()
                val dotDistance = radius * 0.75f
                
                val x = center.x + cos(angle) * dotDistance
                val y = center.y + sin(angle) * dotDistance
                
                val alpha = (sin(i * 0.3f + rotation * 0.02f) + 1f) / 2f * 0.9f + 0.1f
                val scale = (sin(i * 0.2f + rotation * 0.01f) + 1f) / 2f * 0.5f + 0.5f
                
                drawCircle(
                    color = Color(0xFF10A37F).copy(alpha = alpha),
                    radius = dotRadius * scale,
                    center = Offset(x, y)
                )
            }
            
        }
    }
}

