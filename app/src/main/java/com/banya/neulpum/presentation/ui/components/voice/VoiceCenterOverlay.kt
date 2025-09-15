package com.banya.neulpum.presentation.ui.components.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.banya.neulpum.presentation.ui.components.EqualizerCenterReactive
import com.banya.neulpum.presentation.ui.components.voice.CircularAudioWave
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@Composable
fun VoiceCenterOverlay(
    isAwaitingResponse: Boolean,
    isPlaying: Boolean,
    isRecording: Boolean,
    audioLevel: Float,
    contentPadding: PaddingValues
) {
    // Center within the visible content area (excludes top app bar/bottom insets)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        // Simple, always visible equalizer
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    color = if (isRecording) Color(0xFFEA4335) else Color(0xFF10A37F),
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner pulsing circle
            Box(
                modifier = Modifier
                    .size((100f + audioLevel * 50f).dp)
                    .background(
                        color = Color.White.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            
            // Audio level bars around the circle
            if (isRecording || isPlaying) {
                val numBars = 8
                for (i in 0 until numBars) {
                    val angle = (i * 360f / numBars) * kotlin.math.PI / 180f
                    val barHeight = (20f + audioLevel * 30f).dp
                    val x = kotlin.math.cos(angle).toFloat() * 80f
                    val y = kotlin.math.sin(angle).toFloat() * 80f
                    
                    Box(
                        modifier = Modifier
                            .offset(x = x.dp, y = y.dp)
                            .size(4.dp, barHeight)
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                }
            }
        }
    }
}


