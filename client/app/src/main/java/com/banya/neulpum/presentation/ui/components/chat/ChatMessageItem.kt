package com.banya.neulpum.presentation.ui.components.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banya.neulpum.domain.entity.ChatMessage
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.commonmark.parser.Parser
import org.commonmark.node.*

@Composable
fun ChatMessageItem(message: ChatMessage, onTypingFinished: (String) -> Unit = {}) {
    val backgroundColor = if (message.isUser) Color(0xFF64B5F6) else Color(0xFFE1F5FE)
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
        // 말 풍선 꼬리 추가
        if (message.isUser) {
            // 사용자 메시지: 오른쪽에 꼬리
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = SpeechBubbleShape(isUser = message.isUser)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (message.isUser) {
                    // 사용자 메시지는 일반 텍스트로 표시
                    visibleText.split("\\n").forEach { line ->
                        Text(
                            text = line,
                            color = textColor,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                } else {
                    // AI 메시지는 마크다운으로 렌더링
                    val annotatedString = remember(visibleText) {
                        parseMarkdown(visibleText, textColor)
                    }
                    Text(
                        text = annotatedString,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.fillMaxWidth()
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
        
        // 말 풍선 꼬리 추가
        if (!message.isUser) {
            // AI 메시지: 왼쪽에 꼬리
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

// 말 풍선 모양 Shape
class SpeechBubbleShape(private val isUser: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): androidx.compose.ui.graphics.Outline {
        val cornerRadius = with(density) { 16.dp.toPx() }
        val tailSize = with(density) { 8.dp.toPx() }
        val tailOffset = with(density) { 20.dp.toPx() }
        
        return androidx.compose.ui.graphics.Outline.Generic(
            Path().apply {
                if (isUser) {
                    // 사용자 메시지: 오른쪽에 꼬리
                    // 왼쪽 상단
                    moveTo(cornerRadius, 0f)
                    // 오른쪽 상단 (꼬리 전)
                    lineTo(size.width - cornerRadius - tailSize, 0f)
                    // 오른쪽 상단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = size.width - cornerRadius * 2 - tailSize,
                            top = 0f,
                            right = size.width - tailSize,
                            bottom = cornerRadius * 2
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    // 꼬리 (오른쪽)
                    lineTo(size.width - tailSize, tailOffset)
                    lineTo(size.width, tailOffset + tailSize)
                    lineTo(size.width - tailSize, tailOffset + tailSize * 2)
                    // 오른쪽 하단 (꼬리 후)
                    lineTo(size.width - tailSize, size.height - cornerRadius)
                    // 오른쪽 하단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = size.width - cornerRadius * 2 - tailSize,
                            top = size.height - cornerRadius * 2,
                            right = size.width - tailSize,
                            bottom = size.height
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    // 하단
                    lineTo(cornerRadius, size.height)
                    // 왼쪽 하단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = 0f,
                            top = size.height - cornerRadius * 2,
                            right = cornerRadius * 2,
                            bottom = size.height
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    // 왼쪽
                    lineTo(0f, cornerRadius)
                    // 왼쪽 상단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = 0f,
                            top = 0f,
                            right = cornerRadius * 2,
                            bottom = cornerRadius * 2
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                } else {
                    // AI 메시지: 왼쪽에 꼬리
                    // 왼쪽 상단 (꼬리 전)
                    moveTo(cornerRadius + tailSize, 0f)
                    // 오른쪽 상단
                    lineTo(size.width - cornerRadius, 0f)
                    // 오른쪽 상단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = size.width - cornerRadius * 2,
                            top = 0f,
                            right = size.width,
                            bottom = cornerRadius * 2
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    // 오른쪽
                    lineTo(size.width, size.height - cornerRadius)
                    // 오른쪽 하단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = size.width - cornerRadius * 2,
                            top = size.height - cornerRadius * 2,
                            right = size.width,
                            bottom = size.height
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    // 하단
                    lineTo(cornerRadius + tailSize, size.height)
                    // 왼쪽 하단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = tailSize,
                            top = size.height - cornerRadius * 2,
                            right = cornerRadius * 2 + tailSize,
                            bottom = size.height
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    // 꼬리 (왼쪽)
                    lineTo(tailSize, tailOffset + tailSize * 2)
                    lineTo(0f, tailOffset + tailSize)
                    lineTo(tailSize, tailOffset)
                    // 왼쪽 상단 (꼬리 후)
                    lineTo(tailSize, cornerRadius)
                    // 왼쪽 상단 모서리
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = tailSize,
                            top = 0f,
                            right = cornerRadius * 2 + tailSize,
                            bottom = cornerRadius * 2
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                }
                close()
            }
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val time = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(time)
}

// 마크다운 파싱 및 AnnotatedString 생성
private fun parseMarkdown(text: String, defaultColor: Color): androidx.compose.ui.text.AnnotatedString {
    val parser = Parser.builder().build()
    val document = parser.parse(text)
    
    return buildAnnotatedString {
        val visitor = object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                val level = heading.level
                val fontWeight = when (level) {
                    1 -> FontWeight.Bold
                    2 -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
                val fontSize = when (level) {
                    1 -> 20.sp
                    2 -> 18.sp
                    else -> 16.sp
                }
                withStyle(SpanStyle(fontWeight = fontWeight, fontSize = fontSize, color = defaultColor)) {
                    visitChildren(heading)
                }
            }
            
            override fun visit(code: Code) {
                withStyle(SpanStyle(
                    background = defaultColor.copy(alpha = 0.1f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = defaultColor
                )) {
                    append(code.literal)
                }
            }
            
            override fun visit(emphasis: Emphasis) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                    visitChildren(emphasis)
                }
            }
            
            override fun visit(strongEmphasis: StrongEmphasis) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                    visitChildren(strongEmphasis)
                }
            }
            
            override fun visit(link: Link) {
                withStyle(SpanStyle(
                    color = Color(0xFF2196F3),
                    textDecoration = TextDecoration.Underline
                )) {
                    visitChildren(link)
                }
            }
            
            override fun visit(text: Text) {
                append(text.literal)
            }
            
            override fun visit(paragraph: Paragraph) {
                visitChildren(paragraph)
                append("\n")
            }
            
            override fun visit(bulletList: BulletList) {
                visitChildren(bulletList)
            }
            
            override fun visit(orderedList: OrderedList) {
                visitChildren(orderedList)
            }
            
            override fun visit(listItem: ListItem) {
                append("• ")
                visitChildren(listItem)
                append("\n")
            }
            
            override fun visit(blockQuote: BlockQuote) {
                withStyle(SpanStyle(
                    color = defaultColor.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                )) {
                    append("> ")
                    visitChildren(blockQuote)
                }
            }
            
            override fun visit(thematicBreak: ThematicBreak) {
                append("\n---\n")
            }
        }
        
        document.accept(visitor)
    }
}


