package com.banya.neulpum.presentation.ui.components.voice

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
 
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun VoiceMicButton(
    isRecording: Boolean,
    isPlaying: Boolean = false,
    paddingValues: PaddingValues,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isRecording -> Color(0xFFEA4335) // 빨간색: 녹음 중
        else -> Color(0xFF10A37F)        // 초록색: 대기 및 재생 중
    }
    val icon = when {
        isRecording -> Icons.Filled.Stop
        isPlaying -> Icons.Filled.Stop
        else -> Icons.Filled.Mic
    }
    val contentDesc = when {
        isRecording -> "녹음 중지"
        isPlaying -> "재생 중지"
        else -> "음성 입력"
    }

    FloatingActionButton(
        onClick = onToggle,
        modifier = modifier
            .padding(bottom = 24.dp)
            .size(80.dp),
        containerColor = containerColor,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = Color.White
        )
    }
}


