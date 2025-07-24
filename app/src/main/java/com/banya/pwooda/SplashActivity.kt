package com.banya.pwooda

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen(
                onVideoComplete = {
                    // 페이드 아웃 애니메이션 후 MainActivity로 이동
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            )
        }
    }
}

@Composable
fun SplashScreen(onVideoComplete: () -> Unit) {
    val context = LocalContext.current
    var isTransitioning by remember { mutableStateOf(false) }
    
    // 페이드 아웃 애니메이션
    val alpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "fadeOut"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(alpha)
    ) {
        // VideoView를 Compose에서 사용
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    // intro.mp4 파일을 VideoView에 설정
                    val videoPath = "android.resource://" + context.packageName + "/" + R.raw.intro
                    setVideoURI(Uri.parse(videoPath))
                    
                    // 비디오 재생 완료 후 콜백 호출
                    setOnCompletionListener {
                        isTransitioning = true
                        // 페이드 아웃 애니메이션이 완료된 후 MainActivity로 이동
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(500) // 애니메이션 지속 시간만큼 대기
                            onVideoComplete()
                        }
                    }
                    
                    // 비디오 재생 오류 처리
                    setOnErrorListener { mp, what, extra ->
                        Toast.makeText(context, "비디오 로딩 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                        isTransitioning = true
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(500)
                            onVideoComplete()
                        }
                        true
                    }
                    
                    // 비디오 재생 시작
                    start()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { 
                    isTransitioning = true
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        onVideoComplete()
                    }
                } // 화면 터치 시 스킵
        )
        
        // 스킵 버튼
        Button(
            onClick = { 
                isTransitioning = true
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    onVideoComplete()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                text = "건너뛰기",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
} 