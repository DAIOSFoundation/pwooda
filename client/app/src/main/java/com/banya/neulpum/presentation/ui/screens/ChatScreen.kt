package com.banya.neulpum.presentation.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.banya.neulpum.presentation.ui.components.chat.WorkflowStep
import com.banya.neulpum.presentation.ui.components.chat.WorkflowStepsCard
import com.banya.neulpum.presentation.ui.components.chat.ChatMessageItem
import com.banya.neulpum.presentation.ui.components.chat.AnimatedDots
import com.banya.neulpum.data.remote.ChatSSEEvent
import com.banya.neulpum.domain.entity.ChatMessage
import com.banya.neulpum.domain.repository.ChatRepository
import com.banya.neulpum.data.repository.ChatRepositoryImpl
import com.banya.neulpum.presentation.ui.components.CameraComponent
import com.banya.neulpum.presentation.viewmodel.ChatViewModel
import com.banya.neulpum.utils.PermissionHelper
import com.banya.neulpum.utils.rememberPermissionHelper
import com.banya.neulpum.utils.rememberPermissionLauncher
import com.banya.neulpum.domain.entity.ConversationWithMessages
import com.banya.neulpum.data.remote.GoogleSpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.util.*

 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    chatViewModel: ChatViewModel? = null,
    currentConversationId: String? = null,
    onMessageSent: (String) -> Unit = {},
    onConversationCreated: (String) -> Unit = {},
    chatHistoryRepository: com.banya.neulpum.domain.repository.ChatHistoryRepository? = null,
    apiKey: String = ""
) {
    val context = LocalContext.current
    val permissionHelper = rememberPermissionHelper()
    
    var messageText by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isListening by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var currentSteps by remember { mutableStateOf(listOf<WorkflowStep>()) }
    var currentTool by remember { mutableStateOf<String?>(null) }
    var currentStepDetail by remember { mutableStateOf<String?>(null) }
    var activeConversationId by remember { mutableStateOf<String?>(currentConversationId) }
    
    // TTS 관련 상태
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var googleSpeechService by remember { mutableStateOf<GoogleSpeechService?>(null) }
    
    // 권한 관련 상태
    var hasCameraPermission by remember {
        mutableStateOf(
            permissionHelper.isPermissionGranted(PermissionHelper.CAMERA_PERMISSION)
        )
    }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    
    // 카메라 권한 요청 런처
    val cameraPermissionLauncher = rememberPermissionLauncher { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            showCamera = true
        } else {
            showCameraPermissionDialog = true
        }
    }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // currentConversationId가 변경될 때 채팅 처리
    LaunchedEffect(currentConversationId) {
        if (currentConversationId != null && currentConversationId != "new") {
            // 기존 대화 로드
            if (chatHistoryRepository != null) {
                try {
                    isProcessing = true
                    chatHistoryRepository.getConversationWithMessages(apiKey, currentConversationId).fold(
                        onSuccess = { conversationWithMessages ->
                            chatMessages = conversationWithMessages.messages
                            messageText = ""
                            isProcessing = false
                            currentSteps = emptyList()
                            currentTool = null
                            currentStepDetail = null
                            capturedImage = null
                            activeConversationId = conversationWithMessages.conversation.id
                            // 히스토리 로드 후 맨 아래로 스크롤
                            scope.launch {
                                if (chatMessages.isNotEmpty()) {
                                    listState.scrollToItem(chatMessages.size - 1)
                                }
                            }
                        },
                        onFailure = { error ->
                            // 실패 시 새 채팅으로 초기화
                            chatMessages = emptyList()
                            messageText = ""
                            isProcessing = false
                            currentSteps = emptyList()
                            currentTool = null
                            currentStepDetail = null
                            capturedImage = null
                        }
                    )
                } catch (e: Exception) {
                    // 에러 시 새 채팅으로 초기화
                    chatMessages = emptyList()
                    messageText = ""
                    isProcessing = false
                    currentSteps = emptyList()
                    currentTool = null
                    currentStepDetail = null
                    capturedImage = null
                }
            }
        } else {
            // 새 채팅인 경우 초기화
            chatMessages = emptyList()
            messageText = ""
            isProcessing = false
            currentSteps = emptyList()
            currentTool = null
            currentStepDetail = null
            capturedImage = null
            activeConversationId = null
        }
    }
    
    // 메시지 목록이 업데이트될 때 자동 스크롤
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }
    
    // ChatRepository 및 TTS 서비스 초기화
    LaunchedEffect(Unit) {
        // Google TTS API 키로 GoogleSpeechService 초기화
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val googleApiKey = prefs.getString("google_api_key", null)
        if (!googleApiKey.isNullOrEmpty()) {
            googleSpeechService = GoogleSpeechService(context, googleApiKey)
        }
    }
    
    // 화면 종료 시 MediaPlayer 정리
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // 마지막 메시지가 타이핑 중일 때(긴 답변) 바닥으로 지속 스크롤
    LaunchedEffect(chatMessages) {
        val last = chatMessages.lastOrNull()
        if (last != null && !last.isUser && last.isTyping) {
            repeat(80) { // 약 4초 동안 50ms 간격
                listState.scrollToItem(chatMessages.size - 1)
                delay(50)
            }
        }
    }
    
    // ChatRepository 및 TTS 서비스 초기화
    LaunchedEffect(Unit) {
        // Google TTS API 키로 GoogleSpeechService 초기화
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val googleApiKey = prefs.getString("google_api_key", null)
        if (!googleApiKey.isNullOrEmpty()) {
            googleSpeechService = GoogleSpeechService(context, googleApiKey)
        }
    }
    
    // 화면 종료 시 MediaPlayer 정리
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    // 음성 인식 런처
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0].trim()
                // 음성 인식 결과를 입력칸에 채우고 자동 전송
                messageText = recognizedText
                if (recognizedText.isNotEmpty() && !isProcessing) {
                    val currentMessage = recognizedText
                    val currentImage = capturedImage
                    messageText = ""
                    capturedImage = null
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        sendMessage(currentMessage, currentImage, chatMessages, currentSteps, isProcessing, currentTool, context, activeConversationId, { newMessages, newSteps, processing, tool, stepDetail ->
                            chatMessages = newMessages
                            currentSteps = newSteps
                            isProcessing = processing
                            currentTool = tool
                            currentStepDetail = if (newSteps.isNotEmpty()) newSteps.last().detail else stepDetail
                        }, { convId -> activeConversationId = convId }, { /* onConversationCreated handled in MainActivity */ }, onMessageSent)
                    }
                }
            }
        }
    }
    
    
    val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    
    if (showCamera) {
        CameraComponent(
            onImageCaptured = { bitmap ->
                // 촬영 즉시 전송하지 않음. 미리보기로 보여주고, 전송 버튼 눌렀을 때 함께 전송.
                capturedImage = bitmap
                showCamera = false
            },
            onError = { error ->
                showCamera = false
                val errorMessage = ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    content = "카메라 오류: $error",
                    isUser = false
                )
                chatMessages = chatMessages + errorMessage
            },
            onClose = {
                showCamera = false
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
        ) {
            
            // 채팅 메시지 영역
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 최근 채팅에서 히스토리 불러오는 동안에는 환영 문구 대신 로딩 표시
                if (isProcessing && chatMessages.isEmpty() && currentConversationId != null && currentConversationId != "new") {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF64B5F6),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "대화 불러오는 중",
                                    fontSize = 16.sp,
                                    color = Color(0xFF64B5F6)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AnimatedDots()
                            }
                        }
                    }
                } else if (chatMessages.isEmpty()) {
                    item {
                        // 환영 메시지
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "안녕하세요! 👋",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "저는 늘품 AI 어시스턴트입니다.\n무엇을 도와드릴까요?",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(items = chatMessages, key = { it.id ?: it.hashCode().toString() }) { message ->
                        ChatMessageItem(
                            message = message,
                            onTypingFinished = { finishedId ->
                                // Persist typing completion to prevent re-animation
                                chatMessages = chatMessages.map { m ->
                                    if (m.id == finishedId) {
                                        val updated = m.copy(isTyping = false, visibleText = m.content)
                                        // AI 응답 완료 시 TTS로 읽어주기
                                        if (!updated.isUser && updated.content.isNotEmpty()) {
                                            speakText(updated.content, context, googleSpeechService, mediaPlayer) { mp ->
                                                mediaPlayer = mp
                                            }
                                        }
                                        updated
                                    } else {
                                        m
                                    }
                                }
                            }
                        )
                    }
                    
                    // 처리 중일 때 로딩 표시
                    if (isProcessing) {
                        item {
                            if (currentSteps.isNotEmpty()) {
                                // 단계별 진행 상황 표시
                                WorkflowStepsCard(
                                    steps = currentSteps,
                                    currentTool = currentTool,
                                    isLoading = true
                                )
                            } else {
                                // 초기 로딩 표시
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF64B5F6),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = when (currentStepDetail) {
                                                "request received" -> "요청을 받았습니다"
                                                "planning" -> "계획을 세우는 중..."
                                                "planned" -> "계획 완료"
                                                "summarizing" -> "요약 중..."
                                                else -> currentStepDetail ?: ""
                                            },
                                            fontSize = 16.sp,
                                            color = Color(0xFF64B5F6)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        // 점점점 애니메이션
                                        AnimatedDots()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 하단 입력 영역
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 상단 구분선
                    HorizontalDivider(
                        color = Color.LightGray,
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    // 첨부 이미지 미리보기 (오버레이 X 아이콘)
                    if (capturedImage != null) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .size(56.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = capturedImage!!.asImageBitmap(),
                                contentDescription = "첨부 이미지",
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            IconButton(
                                onClick = { capturedImage = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "이미지 삭제",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    // 입력 필드와 버튼들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 카메라 버튼 (바로 카메라 열기)
                        IconButton(
                            onClick = {
                                if (hasCameraPermission) {
                                    showCamera = true
                                } else {
                                    // 카메라 권한 요청
                                    cameraPermissionLauncher.launch(PermissionHelper.CAMERA_PERMISSION)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "카메라",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // 텍스트 입력 필드 (마이크 아이콘 포함)
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("메시지를 입력하세요...", color = Color.Gray) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF64B5F6),
                                unfocusedBorderColor = Color.LightGray,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                cursorColor = Color(0xFF64B5F6)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            enabled = !isProcessing,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (hasAudioPermission) {
                                            startSpeechRecognition(context, speechLauncher)
                                            isListening = true
                                        } else {
                                            val permissionMessage = ChatMessage(
                                                id = System.currentTimeMillis().toString(),
                                                content = "마이크 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                                                isUser = false
                                            )
                                            chatMessages = chatMessages + permissionMessage
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "음성 인식",
                                        tint = if (isListening) Color(0xFF64B5F6) else Color(0xFF64B5F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                        
                        // 전송 버튼
                        IconButton(
                            onClick = {
                                if (!isProcessing && (messageText.isNotEmpty() || capturedImage != null)) {
                                    val currentMessage = messageText
                                    val currentImage = capturedImage
                                    messageText = ""
                                    capturedImage = null
                                    
                                    CoroutineScope(Dispatchers.Main).launch {
                                        sendMessage(currentMessage, currentImage, chatMessages, currentSteps, isProcessing, currentTool, context, activeConversationId, { newMessages, newSteps, processing, tool, stepDetail ->
                                            chatMessages = newMessages
                                            currentSteps = newSteps
                                            isProcessing = processing
                                            currentTool = tool
                                            // currentSteps의 마지막 step의 detail을 사용
                                            currentStepDetail = if (newSteps.isNotEmpty()) newSteps.last().detail else stepDetail
                                        }, { convId -> activeConversationId = convId }, { /* onConversationCreated handled in MainActivity */ }, onMessageSent)
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            enabled = !isProcessing
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "전송",
                                tint = if (messageText.isNotEmpty() && !isProcessing) Color(0xFF64B5F6) else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 카메라 권한 다이얼로그 (권한이 거부되었을 때 설정으로 안내)
    if (showCameraPermissionDialog) {
        com.banya.neulpum.presentation.ui.components.CameraPermissionDialog(
            onConfirm = {
                showCameraPermissionDialog = false
                permissionHelper.openAppSettings()
            },
            onDismiss = {
                showCameraPermissionDialog = false
            }
        )
    }
}

 

 

 

 

private suspend fun sendMessage(
    content: String,
    image: Bitmap?,
    currentMessages: List<ChatMessage>,
    currentSteps: List<WorkflowStep>,
    isProcessing: Boolean,
    currentTool: String?,
    context: Context?,
    conversationId: String?,
    onUpdate: (List<ChatMessage>, List<WorkflowStep>, Boolean, String?, String?) -> Unit,
    onConversationId: (String) -> Unit = {},
    onConversationCreated: (String) -> Unit = {},
    onMessageSent: (String) -> Unit = {}
) {
    val userMessage = ChatMessage(
        id = System.currentTimeMillis().toString(),
        content = content,
        isUser = true,
        image = image
    )
    
    val newMessages = currentMessages + userMessage
    onUpdate(newMessages, currentSteps, true, null, null)
    
    // 최근 채팅 목록에 추가
    onMessageSent(content)
    
        // ChatRepository를 통한 채팅 시작
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val repository = ChatRepositoryImpl()
                
                // organizationApiKey 가져오기
                val organizationApiKey = if (context != null) {
                    val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.getString("organization_api_key", null)
                } else {
                    null
                }

                // ChatRepository를 통해 메시지 전송
                repository.sendMessage(
                    message = content,
                    organizationApiKey = organizationApiKey,
                    providerId = null
                ).collect { chatMessage ->
                    // ChatMessage를 ChatSSEEvent로 변환하여 처리
                    when {
                        chatMessage.isStreaming && chatMessage.toolName != null -> {
                            val event = ChatSSEEvent.Step(
                                stage = "processing",
                                detail = chatMessage.content,
                                tool = chatMessage.toolName,
                                result = chatMessage.toolResult
                            )
                            handleChatSSEEvent(event, newMessages, currentSteps, onUpdate, onConversationId, onConversationCreated, onMessageSent, content)
                        }
                        !chatMessage.isStreaming && chatMessage.content.isNotEmpty() -> {
                            val event = ChatSSEEvent.Final(
                                result = chatMessage.content,
                                conversationId = null
                            )
                            handleChatSSEEvent(event, newMessages, currentSteps, onUpdate, onConversationId, onConversationCreated, onMessageSent, content)
                        }
                    }
                }
            } catch (e: Exception) {
                val errorEvent = ChatSSEEvent.Error(
                    message = e.message ?: "알 수 없는 오류"
                )
                handleChatSSEEvent(errorEvent, newMessages, currentSteps, onUpdate, onConversationId, onConversationCreated, onMessageSent, content)
            }
        }
}

// ChatSSEEvent 처리 헬퍼 함수
private fun handleChatSSEEvent(
    event: ChatSSEEvent,
    newMessages: List<ChatMessage>,
    currentSteps: List<WorkflowStep>,
    onUpdate: (List<ChatMessage>, List<WorkflowStep>, Boolean, String?, String?) -> Unit,
    onConversationId: (String) -> Unit,
    onConversationCreated: (String) -> Unit,
    onMessageSent: (String) -> Unit,
    content: String
) {
    when (event) {
        is ChatSSEEvent.Step -> {
            val newStep = WorkflowStep(
                stage = event.stage,
                detail = event.detail,
                tool = event.tool,
                result = event.result
            )
            val updatedSteps = currentSteps + newStep
            
            // 서버의 실제 타이밍에 맞춰서 즉시 업데이트
            onUpdate(newMessages, updatedSteps, true, event.tool, event.detail)
        }
        is ChatSSEEvent.Final -> {
            val aiMessage = ChatMessage(
                id = (System.currentTimeMillis() + 1).toString(),
                content = event.result,
                isUser = false,
                isTyping = true // 타이핑 효과 활성화
            )
            onUpdate(newMessages + aiMessage, currentSteps, false, null, null)
            event.conversationId?.let {
                onConversationId(it)
                onConversationCreated(it)
            }
            // 최종 수신 시에도 최근 채팅 갱신 트리거
            onMessageSent(content)
        }
        is ChatSSEEvent.Error -> {
            val errorMessage = ChatMessage(
                id = (System.currentTimeMillis() + 1).toString(),
                content = "❌ 오류: ${event.message}",
                isUser = false,
                isTyping = true // 타이핑 효과 활성화
            )
            onUpdate(newMessages + errorMessage, currentSteps, false, null, null)
        }
        is ChatSSEEvent.Unknown -> {
            // 알 수 없는 이벤트 무시
        }
    }
}

private fun startSpeechRecognition(
    content: String,
    image: Bitmap?,
    currentMessages: List<ChatMessage>,
    currentSteps: List<WorkflowStep>,
    isProcessing: Boolean,
    currentTool: String?,
    context: Context?,
    conversationId: String?,
    onUpdate: (List<ChatMessage>, List<WorkflowStep>, Boolean, String?, String?) -> Unit,
    onConversationId: (String) -> Unit = {},
    onConversationCreated: (String) -> Unit = {},
    onMessageSent: (String) -> Unit = {}
) {
    val userMessage = ChatMessage(
        id = System.currentTimeMillis().toString(),
        content = content,
        isUser = true,
        image = image
    )
    
    val newMessages = currentMessages + userMessage
    onUpdate(newMessages, currentSteps, true, null, null)
    
    // 최근 채팅 목록에 추가
    onMessageSent(content)
    
    // ChatRepository를 통한 채팅 시작
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val repository = ChatRepositoryImpl()
            
            // organizationApiKey 가져오기
            val organizationApiKey = if (context != null) {
                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                prefs.getString("organization_api_key", null)
            } else {
                null
            }

            // ChatRepository를 통해 메시지 전송
            repository.sendMessage(
                message = content,
                organizationApiKey = organizationApiKey,
                providerId = null
            ).collect { chatMessage ->
                // ChatMessage를 ChatSSEEvent로 변환하여 처리
                when {
                    chatMessage.isStreaming && chatMessage.toolName != null -> {
                        val event = ChatSSEEvent.Step(
                            stage = "processing",
                            detail = chatMessage.content,
                            tool = chatMessage.toolName,
                            result = chatMessage.toolResult
                        )
                        handleChatSSEEvent(event, newMessages, currentSteps, onUpdate, onConversationId, onConversationCreated, onMessageSent, content)
                    }
                    !chatMessage.isStreaming && chatMessage.content.isNotEmpty() -> {
                        val event = ChatSSEEvent.Final(
                            result = chatMessage.content,
                            conversationId = null
                        )
                        handleChatSSEEvent(event, newMessages, currentSteps, onUpdate, onConversationId, onConversationCreated, onMessageSent, content)
                    }
                }
            }
        } catch (e: Exception) {
            val errorEvent = ChatSSEEvent.Error(
                message = e.message ?: "알 수 없는 오류"
            )
            handleChatSSEEvent(errorEvent, newMessages, currentSteps, onUpdate, onConversationId, onConversationCreated, onMessageSent, content)
        }
    }
}


private fun startSpeechRecognition(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "질문을 말씀해 주세요")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        
        // 음성 인식 대기 시간 설정
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
    }
    
    launcher.launch(intent)
}

private fun formatTime(timestamp: Long): String {
    val time = java.util.Date(timestamp)
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(time)
}

// TTS로 텍스트 읽어주기
private fun speakText(
    text: String,
    context: Context?,
    speechService: GoogleSpeechService?,
    currentMediaPlayer: MediaPlayer?,
    onMediaPlayerUpdate: (MediaPlayer?) -> Unit
) {
    if (context == null || text.isEmpty()) return
    
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 이전 재생 중지
            currentMediaPlayer?.stop()
            currentMediaPlayer?.release()
            
            // Google TTS API 키 가져오기
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val googleApiKey = prefs.getString("google_api_key", null)
            
            if (googleApiKey.isNullOrEmpty()) {
                // API 키가 없으면 Android 기본 TTS 사용
                android.speech.tts.TextToSpeech(context, null)?.apply {
                    setLanguage(java.util.Locale.KOREAN)
                    speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                    shutdown()
                }
                return@launch
            }
            
            // GoogleSpeechService 사용
            val service = speechService ?: GoogleSpeechService(context, googleApiKey)
            
            // TTS 생성 및 재생
            service.synthesizeSpeech(text) { audioData ->
                try {
                    // 임시 파일에 저장
                    val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                    FileOutputStream(tempFile).use { it.write(audioData) }
                    
                    // MediaPlayer로 재생
                    val mp = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            it.release()
                            tempFile.delete()
                            onMediaPlayerUpdate(null)
                        }
                        start()
                    }
                    onMediaPlayerUpdate(mp)
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "TTS 재생 오류", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatScreen", "TTS 생성 오류", e)
        }
    }
}

 
