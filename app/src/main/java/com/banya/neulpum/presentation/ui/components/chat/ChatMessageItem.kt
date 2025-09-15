package com.banya.neulpum.presentation.ui.components.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banya.neulpum.domain.entity.ChatMessage
import kotlinx.coroutines.delay

@Composable
fun ChatMessageItem(message: ChatMessage, onTypingFinished: (String) -> Unit = {}) {
    val backgroundColor = if (message.isUser) Color(0xFF10A37F) else Color(0xFFF7F7F8)
    val textColor = if (message.isUser) Color.White else Color.Black

    var visibleText by remember { mutableStateOf(message.visibleText) }
    var isTyping by remember { mutableStateOf(message.isTyping) }

    LaunchedEffect(message.id, message.content, message.isTyping) {
        if (message.isTyping && !message.isUser) {
            visibleText = ""
            isTyping = true
            for (i in 0..message.content.length) {
                visibleText = message.content.substring(0, i)
                delay(22)
            }
            isTyping = false
            onTypingFinished(message.id ?: "")
        } else {
            visibleText = message.content
            isTyping = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                visibleText.split("\\n").forEach { line ->
                    Text(
                        text = line,
                        color = textColor,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
                if (isTyping) {
                    Text(
                        text = "|",
                        color = textColor,
                        fontSize = 16.sp
                    )
                }
                if (message.image != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "이미지가 첨부되었습니다",
                        color = if (message.isUser) Color.White.copy(alpha = 0.8f) else Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatTime(message.timestamp),
                    color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val time = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(time)
}


