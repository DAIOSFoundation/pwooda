package com.banya.neulpum.presentation.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.banya.neulpum.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit
import android.media.audiofx.Visualizer
import kotlin.math.sqrt

data class VoiceSample(
    val name: String, // 파일명 (나중에 사용할 변수)
    val displayName: String, // "음성 1", "음성 2" 등
    val resourceId: Int // R.raw.{파일명}의 리소스 ID
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // SharedPreferences로 선택된 음성 저장/로드
    val prefs = remember { context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE) }
    val selectedVoiceName = remember { mutableStateOf(prefs.getString("selected_voice_name", "leda") ?: "leda") }
    
    // 음성 샘플 목록 로드
    val voiceSamples = remember {
        loadVoiceSamples(context)
    }
    
    // ExoPlayer 인스턴스
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val attrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
            setAudioAttributes(attrs, true)
            volume = 1f
        }
    }
    
    // 현재 재생 중인 샘플 ID
    var currentlyPlayingId by remember { mutableStateOf<Int?>(null) }
    
    // 재생 상태 추적
    var isPlaying by remember { mutableStateOf(false) }
    
    // 재생 진행률 추적
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    
    // 실제 오디오 레벨 추적
    var audioLevels by remember { mutableStateOf(List(13) { 0f }) }
    var visualizer: Visualizer? by remember { mutableStateOf(null) }
    
    // Visualizer 설정
    LaunchedEffect(isPlaying, currentlyPlayingId) {
        if (isPlaying && currentlyPlayingId != null) {
            // 세션 ID 대기
            repeat(10) {
                if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) return@repeat
                delay(50)
            }
            val sessionId = player.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                try {
                    visualizer?.release()
                    visualizer = Visualizer(sessionId).apply {
                        captureSize = Visualizer.getCaptureSizeRange()[0]
                        setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, bytes: ByteArray?, samplingRate: Int) {
                                if (bytes == null) return
                                // 13개의 바를 위한 오디오 레벨 계산
                                val levels = mutableListOf<Float>()
                                val chunkSize = bytes.size / 13
                                for (i in 0 until 13) {
                                    var sum = 0f
                                    val start = i * chunkSize
                                    val end = minOf((i + 1) * chunkSize, bytes.size)
                                    for (j in start until end step 4) {
                                        val b = bytes[j]
                                        val fb = (b.toInt() and 0xFF) - 128
                                        sum += (fb * fb)
                                    }
                                    val rms = sqrt(sum / (chunkSize / 4)) / 128f
                                    levels.add(rms.coerceIn(0f, 1f))
                                }
                                audioLevels = levels
                            }
                            override fun onFftDataCapture(v: Visualizer?, bytes: ByteArray?, samplingRate: Int) {}
                        }, Visualizer.getMaxCaptureRate() / 2, true, false)
                        enabled = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VoiceSettings", "Visualizer error", e)
                }
            }
        } else {
            visualizer?.release()
            visualizer = null
            audioLevels = List(13) { 0f }
        }
    }
    
    // Visualizer 정리
    DisposableEffect(Unit) {
        onDispose {
            visualizer?.release()
            visualizer = null
        }
    }
    
    // 재생 위치 업데이트를 위한 코루틴
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            duration = player.duration
            delay(100) // 100ms마다 업데이트
        }
    }
    
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = player.isPlaying
                if (playbackState == Player.STATE_ENDED) {
                    currentlyPlayingId = null
                    isPlaying = false
                    currentPosition = 0L
                    duration = 0L
                } else if (playbackState == Player.STATE_READY) {
                    duration = player.duration
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && player.playbackState == Player.STATE_ENDED) {
                    currentlyPlayingId = null
                    currentPosition = 0L
                    duration = 0L
                }
            }
        }
        player.addListener(listener)
        
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "음성 설정",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // 모든 음성 샘플을 하나의 리스트로 표시
            voiceSamples.forEach { sample ->
                val isCurrentPlaying = currentlyPlayingId == sample.resourceId && isPlaying
                val isSelected = sample.name == selectedVoiceName.value
                VoiceSampleItem(
                    sample = sample,
                    isPlaying = isCurrentPlaying,
                    isSelected = isSelected,
                    currentPosition = if (isCurrentPlaying) currentPosition else 0L,
                    duration = if (isCurrentPlaying) duration else 0L,
                    audioLevels = if (isCurrentPlaying) audioLevels else List(13) { 0f },
                    onPlayClick = {
                        if (currentlyPlayingId == sample.resourceId && isPlaying) {
                            // 정지
                            player.stop()
                            currentlyPlayingId = null
                            isPlaying = false
                            currentPosition = 0L
                            duration = 0L
                        } else {
                            // 재생
                            player.stop()
                            player.clearMediaItems()
                            val mediaItem = MediaItem.fromUri(
                                android.net.Uri.parse("android.resource://${context.packageName}/${sample.resourceId}")
                            )
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
                            currentlyPlayingId = sample.resourceId
                            isPlaying = true
                        }
                    },
                    onSelectClick = {
                        selectedVoiceName.value = sample.name
                        prefs.edit().putString("selected_voice_name", sample.name).apply()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun VoiceSampleItem(
    sample: VoiceSample,
    isPlaying: Boolean,
    isSelected: Boolean,
    currentPosition: Long,
    duration: Long,
    audioLevels: List<Float>,
    onPlayClick: () -> Unit,
    onSelectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = if (isPlaying) Color(0xFFE8F5E9) else Color.White,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = sample.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.width(60.dp)
            )
            
                // 오디오 파형 시각화
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AudioWaveform(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        isPlaying = isPlaying,
                        progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f,
                        audioLevels = audioLevels
                    )
                
                // 재생 시간 표시
                if (isPlaying && duration > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatVoiceTime(currentPosition),
                            fontSize = 11.sp,
                            color = Color(0xFF10A37F),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "-${formatVoiceTime((duration - currentPosition).coerceAtLeast(0L))}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 선택 버튼
                IconButton(
                    onClick = onSelectClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = if (isSelected) "선택됨" else "선택",
                        tint = if (isSelected) Color(0xFF10A37F) else Color(0xFFCCCCCC),
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // 재생 버튼
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "정지" else "재생",
                        tint = Color(0xFF10A37F),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun formatVoiceTime(timeMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(timeMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun AudioWaveform(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    progress: Float = 0f,
    audioLevels: List<Float> = List(13) { 0f }
) {
    // 실제 오디오 레벨 사용, 없으면 기본값
    val waveformHeights = if (isPlaying && audioLevels.any { it > 0f }) {
        // 최소 높이를 0.2f로 설정하여 항상 보이도록
        audioLevels.map { (it * 0.8f + 0.2f).coerceIn(0.2f, 1f) }
    } else {
        // 재생 중이 아니거나 레벨이 없을 때 기본 패턴
        listOf(0.2f, 0.6f, 1.0f, 0.8f, 0.5f, 0.3f, 0.2f, 0.7f, 1.0f, 0.9f, 0.8f, 0.5f, 0.3f)
    }
    
    // 재생 중일 때 애니메이션을 위한 오프셋
    val animatedOffset by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "waveform_animation"
    )
    
    Canvas(modifier = modifier) {
        val barWidth = size.width / waveformHeights.size
        val maxBarHeight = size.height * 0.8f
        val barSpacing = barWidth * 0.3f
        val actualBarWidth = barWidth - barSpacing
        val progressWidth = size.width * progress
        
        waveformHeights.forEachIndexed { index, height ->
            val barHeight = maxBarHeight * height
            val x = index * barWidth + barSpacing / 2
            val y = (size.height - barHeight) / 2
            
            // 진행률에 따라 활성/비활성 구분
            val isActive = x < progressWidth
            val barColor = when {
                !isPlaying -> Color(0xFFCCCCCC)
                isActive -> Color(0xFF10A37F)
                else -> Color(0xFFE0E0E0)
            }
            
            // 재생 중일 때 약간의 애니메이션 효과
            val animatedHeight = if (isPlaying && isActive) {
                (barHeight * (1f + 0.1f * kotlin.math.sin(animatedOffset * 2f * kotlin.math.PI.toFloat() + index * 0.5f))).coerceAtMost(maxBarHeight)
            } else {
                barHeight
            }
            
            val adjustedY = (size.height - animatedHeight) / 2f
            
            drawRect(
                color = barColor,
                topLeft = Offset(x, adjustedY),
                size = Size(actualBarWidth, animatedHeight)
            )
        }
    }
}

fun loadVoiceSamples(context: Context): List<VoiceSample> {
    val samples = mutableListOf<VoiceSample>()
    
    try {
        // R.raw 클래스의 모든 필드 가져오기
        val rawClass = R.raw::class.java
        val fields: Array<Field> = rawClass.fields
        
        val allSamples = mutableListOf<Pair<String, Int>>()
        
        fields.forEach { field ->
            val fileName = field.name.lowercase()
            val resourceId = field.getInt(null)
            
            // R.raw 필드명은 확장자 없이 파일명만 포함 (예: achernar)
            // 모든 필드를 샘플로 추가
            allSamples.add(fileName to resourceId)
        }
        
        // leda를 첫 번째로, 나머지는 정렬
        val sortedSamples = allSamples.sortedWith { a, b ->
            when {
                a.first == "leda" -> -1
                b.first == "leda" -> 1
                else -> a.first.compareTo(b.first)
            }
        }
        
        // 번호 매기기 (leda가 음성 1)
        sortedSamples.forEachIndexed { index, (name, resourceId) ->
            samples.add(
                VoiceSample(
                    name = name, // 파일명 (예: leda) - 나중에 사용할 변수
                    displayName = "음성 ${index + 1}",
                    resourceId = resourceId
                )
            )
        }
        
        android.util.Log.d("VoiceSettings", "Loaded ${samples.size} voice samples")
    } catch (e: Exception) {
        android.util.Log.e("VoiceSettings", "Error loading voice samples", e)
    }
    
    return samples
}

