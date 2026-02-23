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
import com.banya.neulpum.BuildConfig
import com.banya.neulpum.data.remote.GoogleSpeechService
import com.banya.neulpum.presentation.ui.components.EqualizerBars
import com.banya.neulpum.presentation.ui.components.EqualizerCenterReactive
import com.banya.neulpum.data.remote.TavilyWebSearchService
import com.banya.neulpum.data.remote.VoiceWebSocketClient
import com.banya.neulpum.data.remote.LiveKitRoomManager
import com.banya.neulpum.data.remote.LiveKitTokenService
import com.banya.neulpum.data.remote.RoomState
import com.banya.neulpum.data.remote.AgentMessage
import java.util.UUID
import kotlinx.coroutines.launch
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import com.banya.neulpum.presentation.ui.components.voice.VoiceCenterOverlay
import com.banya.neulpum.presentation.ui.components.voice.VoiceMicButton
import com.banya.neulpum.presentation.ui.components.voice.CircularParticleView
import com.banya.neulpum.utils.PermissionHelper
import com.banya.neulpum.utils.rememberPermissionHelper
import com.banya.neulpum.utils.rememberPermissionLauncher
import com.banya.neulpum.presentation.ui.components.MicrophonePermissionDialog
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.random.Random
import android.media.audiofx.Visualizer
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Phone
import androidx.compose.foundation.clickable

// PCM16LE를 WAV로 변환하는 함수
fun pcm16leToWav(pcmData: ByteArray, sampleRate: Int = 24000, channels: Int = 1): ByteArray {
    val dataSize = pcmData.size
    val fileSize = 36 + dataSize // WAV 헤더(44 bytes) - 8 bytes + dataSize
    
    val wav = ByteArray(44 + dataSize)
    val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
    
    // RIFF 헤더
    buffer.put("RIFF".toByteArray())
    buffer.putInt(fileSize)
    buffer.put("WAVE".toByteArray())
    
    // fmt 청크
    buffer.put("fmt ".toByteArray())
    buffer.putInt(16) // fmt 청크 크기
    buffer.putShort(1.toShort()) // 오디오 포맷 (1 = PCM)
    buffer.putShort(channels.toShort()) // 채널 수
    buffer.putInt(sampleRate) // 샘플 레이트
    buffer.putInt(sampleRate * channels * 2) // 바이트 레이트
    buffer.putShort((channels * 2).toShort()) // 블록 정렬
    buffer.putShort(16.toShort()) // 비트 깊이
    
    // data 청크
    buffer.put("data".toByteArray())
    buffer.putInt(dataSize) // 데이터 크기
    buffer.put(pcmData) // PCM 데이터
    
    return wav
}

// 웹 검색이 필요한지 판단 (일반 챗팅과 동일한 로직)
private fun needsWebSearch(message: String): Boolean {
    val searchKeywords = listOf(
        "최신", "현재", "오늘", "어제", "뉴스", "검색", "찾아", "알려줘", "알아봐줘",
        "트위터", "인스타그램", "페이스북", "구글",
        "what", "when", "where", "who", "how", "latest", "current", "news", "search",
        "twitter", "instagram", "facebook", "google"
    )
    return searchKeywords.any { keyword ->
        message.contains(keyword, ignoreCase = true)
    }
}

// 메시지에서 검색 쿼리 추출 (일반 챗팅과 동일한 로직)
private fun extractSearchQuery(message: String): String {
    return message.trim()
}

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
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // 권한 요청 런처
    val permissionLauncher = rememberPermissionLauncher { isGranted ->
        hasMicrophonePermission = isGranted
        if (!isGranted) {
            // 권한이 거부되었을 때 설정 다이얼로그 표시
            showSettingsDialog = true
        }
    }
    
    // 화면이 표시될 때마다 권한 상태를 다시 확인
    LaunchedEffect(Unit) {
        hasMicrophonePermission = permissionHelper.isPermissionGranted(PermissionHelper.RECORD_AUDIO_PERMISSION)
    }
    
    
    var tavilyService by remember { mutableStateOf<TavilyWebSearchService?>(null) }
    val scope = rememberCoroutineScope()
    
    var wsClient by remember { mutableStateOf<VoiceWebSocketClient?>(null) }
    var isWsConnected by remember { mutableStateOf(false) }

    // LiveKit 관련 상태
    val authPrefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
    val accessToken = remember { authPrefs.getString("access_token", "") ?: "" }
    val userId = remember { authPrefs.getString("user_id", UUID.randomUUID().toString()) ?: UUID.randomUUID().toString() }
    val userName = remember { authPrefs.getString("user_name", "User") ?: "User" }

    val roomManager = remember { LiveKitRoomManager(context) }
    val tokenService = remember { LiveKitTokenService(accessToken) }
    val roomState by roomManager.roomState.collectAsState()
    val isSpeakingFromLiveKit by roomManager.isSpeaking.collectAsState()
    var useLiveKit by remember { mutableStateOf(BuildConfig.USE_LIVEKIT) } // BuildConfig에서 LiveKit 모드 설정
    
    // AudioTrack 사용 (스트리밍 재생)
    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    val audioChunkChannel = remember { Channel<ByteArray>(capacity = Channel.UNLIMITED) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var isAwaitingResponse by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableStateOf(0f) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var doneReceived by remember { mutableStateOf(false) }
    var fallbackScheduled by remember { mutableStateOf(false) }
    var lastAudioFormat by remember { mutableStateOf<String?>(null) }
    var isWsConnecting by remember { mutableStateOf(false) }
    var isCallInProgress by remember { mutableStateOf(false) }
    val mainHandler = remember { androidx.core.os.HandlerCompat.createAsync(android.os.Looper.getMainLooper()) }
    var lastRecognizedText by remember { mutableStateOf<String?>(null) }
    var playbackSessionId by remember { mutableStateOf(0) }
    // 음성 채팅도 일반 채팅과 동일하게 conversationId 사용
    var currentConversationId by remember { mutableStateOf(conversationId) }
    
    // SharedPreferences 참조 (전역으로 사용)
    val voiceSettingsPrefs = remember { context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE) }
    
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

    // LiveKit 에이전트 메시지 수신
    LaunchedEffect(roomState) {
        if (useLiveKit && roomState == RoomState.Connected) {
            roomManager.agentMessages.collect { message ->
                when (message) {
                    is AgentMessage.Text -> {
                        // AI 응답 텍스트 수신
                        android.util.Log.d("VoiceChatScreen", "LiveKit text: ${message.content}")
                    }
                    is AgentMessage.Status -> {
                        when (message.status) {
                            "processing" -> {
                                currentWorkflowStep = "🤔 AI가 응답 중..."
                                isProcessing = true
                                isAwaitingResponse = true
                            }
                            "done" -> {
                                currentWorkflowStep = ""
                                isProcessing = false
                                isAwaitingResponse = false
                                doneReceived = true
                            }
                        }
                    }
                    is AgentMessage.Error -> {
                        android.util.Log.e("VoiceChatScreen", "LiveKit error: ${message.message}")
                        isProcessing = false
                        isAwaitingResponse = false
                    }
                }
            }
        }
    }

    // LiveKit 연결 함수
    fun connectToLiveKit() {
        scope.launch {
            val roomName = "voice-room-$userId-${System.currentTimeMillis()}"
            val newConversationId = currentConversationId ?: UUID.randomUUID().toString()

            currentWorkflowStep = "📞 연결 중..."
            isProcessing = true

            val tokenResult = tokenService.getToken(
                roomName = roomName,
                participantIdentity = userId,
                participantName = userName,
                conversationId = newConversationId,
                tokenServerUrl = null // 기본 서버 사용
            )

            tokenResult.fold(
                onSuccess = { response ->
                    val connectResult = roomManager.connect(
                        url = response.url,
                        token = response.token,
                        enableMicrophone = false
                    )

                    connectResult.fold(
                        onSuccess = {
                            if (currentConversationId == null) {
                                currentConversationId = newConversationId
                                onConversationCreated(newConversationId)
                            }
                            currentWorkflowStep = ""
                            isProcessing = false
                        },
                        onFailure = { error ->
                            android.util.Log.e("VoiceChatScreen", "LiveKit connect failed: ${error.message}")
                            currentWorkflowStep = ""
                            isProcessing = false
                        }
                    )
                },
                onFailure = { error ->
                    android.util.Log.e("VoiceChatScreen", "Token request failed: ${error.message}")
                    currentWorkflowStep = ""
                    isProcessing = false
                }
            )
        }
    }

    // LiveKit 연결 해제
    fun disconnectFromLiveKit() {
        roomManager.disconnect()
    }

    // Twilio 아웃바운드 콜 시작 (서버 API 호출)
    fun initiateOutboundCall() {
        scope.launch {
            isCallInProgress = true
            currentWorkflowStep = "전화 연결 중..."
            isProcessing = true

            try {
                val baseUrl = BuildConfig.LIVEKIT_TOKEN_SERVER_URL
                    .replace(":8081", ":8082")  // twilio-bridge 포트
                val url = "$baseUrl/api/v1/call/initiate"

                android.util.Log.d("VoiceChatScreen", "Initiating call via: $url")

                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val jsonBody = org.json.JSONObject().apply {
                        put("to", "+8201062904539")
                    }

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .post(
                            jsonBody.toString()
                                .toByteArray()
                                .let { okhttp3.RequestBody.create(
                                    "application/json".toMediaType(),
                                    it
                                ) }
                        )
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    android.util.Log.d("VoiceChatScreen", "Call response: ${response.code} - $responseBody")

                    if (response.isSuccessful) {
                        mainHandler.post {
                            currentWorkflowStep = "전화가 곧 울립니다..."
                            isProcessing = false
                        }
                    } else {
                        mainHandler.post {
                            currentWorkflowStep = "전화 연결 실패"
                            isProcessing = false
                            isCallInProgress = false
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceChatScreen", "Call initiation failed: ${e.message}")
                currentWorkflowStep = ""
                isProcessing = false
                isCallInProgress = false
            }
        }
    }

    // Twilio 번호로 직접 전화 (인바운드 콜)
    fun dialTwilioNumber() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+18312733281"))
        context.startActivity(intent)
    }

    // 음성 인식 서비스 및 WebSocket 초기화
    LaunchedEffect(Unit) {
        speechService = GoogleSpeechService(context, "YOUR_GOOGLE_API_KEY")
        tavilyService = TavilyWebSearchService()
        
        // 오디오 볼륨 확인 및 설정
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let { am ->
            val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            android.util.Log.d("VoiceChatScreen", "Current media volume: $currentVolume/$maxVolume")
            if (currentVolume == 0) {
                android.util.Log.w("VoiceChatScreen", "Media volume is 0! User needs to turn up volume.")
            }
        }
        
        // WebSocket 연결 설정
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val selectedVoiceName = voiceSettingsPrefs.getString("selected_voice_name", "leda") ?: "leda"
        val access = prefs.getString("access_token", null)
        val orgKey = prefs.getString("organization_api_key", null)
        val base = com.banya.neulpum.di.AppConfig.BASE_HOST
        val wsScheme = if (base.startsWith("https")) "wss" else "ws"
        val wsUrl = base.replaceFirst(Regex("^https?"), wsScheme) + "/ws/voice"
        // mainHandler 및 debugToast는 Composable 스코프에서 remember로 공유됨
        
        // AudioTrack 초기화 - 버퍼를 충분히 크게 설정하여 끊김 방지
        val sampleRate = 24000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        // 최적 버퍼: minBufferSize의 4배 (안정적인 스트리밍을 위한 충분한 버퍼)
        val bufferSize = minBufferSize * 4
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        // 볼륨 최대로 설정
        try {
            audioTrack?.setVolume(1.0f)
            android.util.Log.d("VoiceChatScreen", "AudioTrack initialized: sampleRate=$sampleRate, bufferSize=$bufferSize, state=${audioTrack?.state}")
        } catch (e: Exception) {
            android.util.Log.e("VoiceChatScreen", "Failed to set volume", e)
        }
            
        wsClient = VoiceWebSocketClient(wsUrl, access, orgKey, object: VoiceWebSocketClient.Listener {
            override fun onOpen() {
                isWsConnected = true
                isWsConnecting = false
                // 연결 지연 시 보낸 텍스트를 즉시 전송
                val p = pendingText
                if (!p.isNullOrBlank()) {
                    val voiceName = voiceSettingsPrefs.getString("selected_voice_name", "leda") ?: "leda"
                    wsClient?.sendText(p, currentConversationId, voiceName) // 기존 대화 사용 (일반 채팅과 동일)
                    pendingText = null
                }
                
            }
            override fun onTtsChunkStream(bytes: ByteArray, format: String?, chunkIndex: Int, isFinal: Boolean) {
                if (bytes.isNotEmpty()) {
                    // 첫 청크를 받으면 재생 세션 시작
                    if (chunkIndex == 0) {
                        mainHandler.post {
                            playbackSessionId++
                            android.util.Log.d("VoiceChatScreen", "New playback session started: #$playbackSessionId")
                        }
                    }
                    
                    val enqueueResult = audioChunkChannel.trySend(bytes.copyOf())
                    if (enqueueResult.isFailure) {
                        android.util.Log.w(
                            "VoiceChatScreen",
                            "Audio chunk enqueue failed: ${enqueueResult.exceptionOrNull()?.message}"
                        )
                    } else {
                        mainHandler.post {
                            isAwaitingResponse = false
                        }
                    }
                }
                
                if (isFinal) {
                    // 빈 배열을 종료 신호로 전송
                    audioChunkChannel.trySend(ByteArray(0))
                    mainHandler.post {
                        doneReceived = true
                        fallbackScheduled = false
                        isAwaitingResponse = false
                    }
                }
            }
            
            override fun onTtsChunk(bytes: ByteArray, format: String?) {
                // 레거시 지원 (한 번에 오는 경우)
                onTtsChunkStream(bytes, format, 0, true)
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
                isWsConnected = false
                isAwaitingResponse = false
                isWsConnecting = false
                android.util.Log.d("VoiceChatScreen", "WebSocket error: $error")
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

    LaunchedEffect(audioTrack, playbackSessionId) {
        val track = audioTrack ?: return@LaunchedEffect
        if (playbackSessionId == 0) return@LaunchedEffect // 초기 상태에서는 실행하지 않음
        
        android.util.Log.d("VoiceChatScreen", "Starting playback session #$playbackSessionId")
        withContext(Dispatchers.IO) {
            try {
                var isFirstChunk = true
                val preBufferList = mutableListOf<ByteArray>()
                var totalBufferedBytes = 0
                val preBufferThreshold = 30720 // 640ms = 80ms × 8개 (24000Hz * 2bytes * 0.64s)
                
                // 청크 안정화: 80ms 단위로 모아서 write (서버에서 80ms로 전송)
                val chunkAccumulator = mutableListOf<ByteArray>()
                var accumulatedBytes = 0
                val chunkWriteThreshold = 3840 // 80ms (24000Hz * 2bytes * 0.08s)
                
                for (chunk in audioChunkChannel) {
                    // 빈 배열이면 종료 신호
                    if (chunk.isEmpty()) {
                        android.util.Log.d("VoiceChatScreen", "Received end-of-stream signal")
                        
                        // Pre-buffer에 남은 데이터가 있으면 재생
                        if (preBufferList.isNotEmpty()) {
                            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                track.play()
                                mainHandler.post {
                                    isPlaying = true
                                    isAwaitingResponse = false
                                }
                            }
                            for (buffered in preBufferList) {
                                track.write(buffered, 0, buffered.size, AudioTrack.WRITE_BLOCKING)
                            }
                        }
                        
                        // Accumulator에 남은 청크도 write
                        if (chunkAccumulator.isNotEmpty()) {
                            val mergedBuffer = ByteArray(accumulatedBytes)
                            var mergedOffset = 0
                            for (accChunk in chunkAccumulator) {
                                System.arraycopy(accChunk, 0, mergedBuffer, mergedOffset, accChunk.size)
                                mergedOffset += accChunk.size
                            }
                            track.write(mergedBuffer, 0, mergedBuffer.size, AudioTrack.WRITE_BLOCKING)
                        }
                        
                        break
                    }
                    
                    // AudioTrack 상태 확인
                    val state = try { track.state } catch (_: Exception) { AudioTrack.STATE_UNINITIALIZED }
                    if (state != AudioTrack.STATE_INITIALIZED) {
                        android.util.Log.w("VoiceChatScreen", "AudioTrack not initialized, stopping playback")
                        break
                    }
                    
                    // 초기 버퍼링: 첫 청크들을 모아서 한번에 재생 시작
                    if (isFirstChunk) {
                        preBufferList.add(chunk)
                        totalBufferedBytes += chunk.size
                        
                        if (totalBufferedBytes >= preBufferThreshold) {
                            android.util.Log.d("VoiceChatScreen", "Pre-buffering complete ($totalBufferedBytes bytes)")
                            isFirstChunk = false
                            
                            // 먼저 버퍼링된 데이터를 모두 AudioTrack에 쓰기
                            var totalWritten = 0
                            for (buffered in preBufferList) {
                                var offset = 0
                                while (offset < buffered.size) {
                                    val written = try {
                                        track.write(buffered, offset, buffered.size - offset, AudioTrack.WRITE_BLOCKING)
                                    } catch (e: Exception) {
                                        android.util.Log.e("VoiceChatScreen", "Write failed", e)
                                        -1
                                    }
                                    if (written <= 0) {
                                        android.util.Log.e("VoiceChatScreen", "Write returned $written, stopping")
                                        break
                                    }
                                    offset += written
                                    totalWritten += written
                                }
                            }
                            android.util.Log.d("VoiceChatScreen", "Pre-buffer written: $totalWritten bytes from ${preBufferList.size} chunks")
                            preBufferList.clear()
                            
                            // 데이터가 충분히 쓰여진 후에 재생 시작
                            try {
                                track.play()
                                android.util.Log.d("VoiceChatScreen", "Playback started after pre-buffering")
                                mainHandler.post {
                                    isPlaying = true
                                    isAwaitingResponse = false
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("VoiceChatScreen", "Failed to start playback", e)
                                break
                            }
                        }
                        continue
                    }
                    
                    // 정상 재생 중: 청크를 모아서 write (20~40ms 분량)
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        try {
                            track.play()
                            android.util.Log.d("VoiceChatScreen", "Resuming playback")
                            mainHandler.post {
                                isPlaying = true
                                isAwaitingResponse = false
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VoiceChatScreen", "Failed to start playback", e)
                            break
                        }
                    }
                    
                    // 청크를 accumulator에 추가
                    chunkAccumulator.add(chunk)
                    accumulatedBytes += chunk.size
                    
                    // 40ms 분량 이상 모였으면 write
                    if (accumulatedBytes >= chunkWriteThreshold) {
                        // 모든 청크를 합쳐서 하나의 버퍼로 만들기
                        val mergedBuffer = ByteArray(accumulatedBytes)
                        var mergedOffset = 0
                        for (accChunk in chunkAccumulator) {
                            System.arraycopy(accChunk, 0, mergedBuffer, mergedOffset, accChunk.size)
                            mergedOffset += accChunk.size
                        }
                        
                        // write
                        var offset = 0
                        while (offset < mergedBuffer.size) {
                            val written = try {
                                track.write(mergedBuffer, offset, mergedBuffer.size - offset, AudioTrack.WRITE_BLOCKING)
                            } catch (e: Exception) {
                                android.util.Log.e("VoiceChatScreen", "Write failed", e)
                                -1
                            }
                            if (written <= 0) {
                                android.util.Log.e("VoiceChatScreen", "Write returned $written at offset $offset/${mergedBuffer.size}")
                                break
                            }
                            offset += written
                        }
                        
                        // 초기화
                        chunkAccumulator.clear()
                        accumulatedBytes = 0
                    }
                }
                
                // 재생 완료 후 AudioTrack 버퍼가 비워질 때까지 대기
                android.util.Log.d("VoiceChatScreen", "Waiting for playback to finish...")
                try {
                    // 버퍼에 남은 데이터가 재생될 시간 확보 (최대 1초)
                    var waitCount = 0
                    while (track.playState == AudioTrack.PLAYSTATE_PLAYING && waitCount < 10) {
                        delay(100)
                        waitCount++
                    }
                } catch (_: Exception) {}
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("VoiceChatScreen", "Audio playback loop failed", e)
            } finally {
                android.util.Log.d("VoiceChatScreen", "Playback finished, stopping AudioTrack")
                try {
                    track.pause()
                    track.flush()
                } catch (_: Exception) {}
                mainHandler.post {
                    isPlaying = false
                    audioLevel = 0f
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioChunkChannel.close()
            try { wsClient?.close() } catch (_: Exception) {}
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            // LiveKit 정리
            roomManager.release()
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
                    isActive = isRecording || isPlaying || isSpeakingFromLiveKit,
                    audioLevel = audioLevel,
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        
        // LiveKit 연결 상태 표시
        if (useLiveKit) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 상태 인디케이터
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = when (roomState) {
                                    is RoomState.Connected -> Color(0xFF4CAF50)
                                    is RoomState.Connecting, is RoomState.Reconnecting -> Color(0xFFFFC107)
                                    is RoomState.Error -> Color(0xFFEA4335)
                                    else -> Color.Gray
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (roomState) {
                            is RoomState.Disconnected -> "연결 안됨"
                            is RoomState.Connecting -> "연결 중..."
                            is RoomState.Connected -> "통화 연결됨"
                            is RoomState.Reconnecting -> "재연결 중..."
                            is RoomState.Error -> "오류"
                        },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
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
                                        Color(0xFF64B5F6).copy(alpha = 0.8f),
                                        Color(0xFF64B5F6).copy(alpha = 0.3f)
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
                        color = Color(0xFF64B5F6),
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
                    
                    EqualizerBars(active = true, barColor = Color(0xFF64B5F6))
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
                        // LiveKit 모드일 때 연결 확인
                        if (useLiveKit) {
                            if (roomState != RoomState.Connected && roomState != RoomState.Connecting) {
                                android.util.Log.d("VoiceChatScreen", "LiveKit not connected, connecting...")
                                connectToLiveKit()
                            } else {
                                android.util.Log.d("VoiceChatScreen", "LiveKit already connected or connecting")
                            }
                        } else {
                            // 기존 WebSocket 모드
                            if (!isWsConnected && !isWsConnecting) {
                                isWsConnecting = true
                                android.util.Log.d("VoiceChatScreen", "WebSocket not connected, attempting to reconnect...")
                                try {
                                    wsClient?.close()
                                    wsClient?.connect()
                                } catch (e: Exception) {
                                    isWsConnecting = false
                                    isWsConnected = false
                                    android.util.Log.e("VoiceChatScreen", "WebSocket connection failed: ${e.message}")
                                }
                            } else if (isWsConnected) {
                                android.util.Log.d("VoiceChatScreen", "WebSocket already connected")
                            }
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
                                    try { 
                                        audioTrack?.pause()
                                        audioTrack?.flush()
                                    } catch (_: Exception) {}
                                    while (audioChunkChannel.tryReceive().isSuccess) { /* drop pending audio */ }
                                    isPlaying = false
                                    audioLevel = 0f
                                    try { visualizer?.release() } catch (_: Exception) {}
                                    visualizer = null
                                    
                                    // 상태 변수들 완전 초기화
                                    doneReceived = false
                                    fallbackScheduled = false
                                    isAwaitingResponse = true
                                    
                                    // 웹 검색이 필요한지 확인하고 수행
                                    scope.launch {
                                        val finalText = if (needsWebSearch(text)) {
                                            try {
                                                // 웹 검색 수행
                                                currentWorkflowStep = "🔍 웹 검색 중..."
                                                isProcessing = true
                                                
                                                val searchQuery = extractSearchQuery(text)
                                                val searchResponse = tavilyService?.search(searchQuery, maxResults = 5)
                                                
                                                if (searchResponse != null) {
                                                    val searchResults = tavilyService?.formatSearchResults(searchResponse) ?: ""
                                                    // 검색 결과를 메시지에 포함
                                                    "$text\n\n=== 웹 검색 결과 ===\n$searchResults"
                                                } else {
                                                    text
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("VoiceChatScreen", "웹 검색 오류", e)
                                                text
                                            } finally {
                                                currentWorkflowStep = ""
                                                isProcessing = false
                                            }
                                        } else {
                                            text
                                        }
                                        
                                        // 서버에 메시지 전송
                                        if (useLiveKit && roomState == RoomState.Connected) {
                                            // LiveKit Data Channel로 전송
                                            roomManager.sendTextMessage(finalText)
                                            android.util.Log.d("VoiceChatScreen", "Sent via LiveKit: $finalText")
                                        } else if (isWsConnected) {
                                            // WebSocket으로 전송 (폴백)
                                            val voiceName = voiceSettingsPrefs.getString("selected_voice_name", "leda") ?: "leda"
                                            wsClient?.sendText(finalText, currentConversationId, voiceName)
                                        } else {
                                            pendingText = finalText
                                        }
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
                        // 마이크 권한이 없는 경우 실제 권한 요청
                        permissionLauncher.launch(PermissionHelper.RECORD_AUDIO_PERMISSION)
                    }
                }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )

            // 전화 걸기 버튼들 (마이크 버튼 양 옆)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(100.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 아웃바운드 콜 (AI가 나에게 전화)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { initiateOutboundCall() },
                        enabled = !isCallInProgress,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (isCallInProgress) Color(0xFFE0E0E0) else Color(0xFF4CAF50),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "AI 전화 받기",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI 전화",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                // 인바운드 콜 (내가 AI에게 전화)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { dialTwilioNumber() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = Color(0xFF2196F3),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "AI에게 전화",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "직접 전화",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

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

    // 오디오 레벨 시각화: 최적화된 Visualizer 사용 (AudioTrack)
    LaunchedEffect(isPlaying, audioTrack) {
        if (isPlaying && audioTrack != null) {
            // AudioTrack 세션 ID 확인
            val sessionId = try {
                audioTrack!!.audioSessionId
            } catch (e: Exception) {
                0
            }
            
            if (sessionId != 0) {
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
    
    // 마이크 권한 요청 다이얼로그 (권한이 거부되었을 때 설정으로 안내)
    if (showSettingsDialog) {
        MicrophonePermissionDialog(
            onConfirm = {
                showSettingsDialog = false
                permissionHelper.openAppSettings()
            },
            onDismiss = {
                showSettingsDialog = false
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
            Color(0xFF64B5F6).copy(alpha = 0.1f),
            Color(0xFF00D4AA).copy(alpha = 0.05f),
            Color(0xFF64B5F6).copy(alpha = 0.1f)
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
                    color = Color(0xFF64B5F6).copy(alpha = alpha),
                    radius = dotRadius * scale,
                    center = Offset(x, y)
                )
            }
            
        }
    }
}

