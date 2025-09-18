package com.banya.neulpum.presentation.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banya.neulpum.data.repository.AuthRepositoryImpl
import com.banya.neulpum.data.repository.ChatRepositoryImpl
import com.banya.neulpum.data.repository.ChatHistoryRepositoryImpl
import com.banya.neulpum.domain.entity.Conversation
import com.banya.neulpum.presentation.ui.screens.ChatScreen
import com.banya.neulpum.presentation.ui.screens.SettingsScreen
import com.banya.neulpum.presentation.ui.screens.VoiceChatScreen
import com.banya.neulpum.presentation.ui.screens.OrganizationScreen
import com.banya.neulpum.presentation.ui.screens.ProfileEditScreen
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import com.banya.neulpum.presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.banya.neulpum.utils.PermissionHelper
import com.banya.neulpum.utils.rememberPermissionHelper
import com.banya.neulpum.utils.rememberPermissionLauncher
import com.banya.neulpum.presentation.ui.components.PermissionDialog
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Storage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 상태바 설정 - 검은색 배경에 흰색 글씨
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // 흰색 글씨
        window.statusBarColor = android.graphics.Color.BLACK // 검은색 배경
        
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    isSelected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF10A37F) else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) Color(0xFF10A37F) else Color.Black
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun DrawerChatItem(
    title: String,
    onClick: () -> Unit,
    isVoiceChat: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isVoiceChat) Icons.Default.Mic else Icons.AutoMirrored.Filled.Chat,
            contentDescription = if (isVoiceChat) "보이스 채팅" else "채팅",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
            )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color.Black,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedScreen by remember { mutableStateOf("voice") }
    var showOrganizationScreen by remember { mutableStateOf(false) }
    var showProfileEditScreen by remember { mutableStateOf(false) }
    var drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var scope = rememberCoroutineScope()
    
    
    // AuthViewModel과 ChatViewModel 생성
    val authViewModel = remember {
        try {
            AuthViewModel(AuthRepositoryImpl(context))
        } catch (e: Exception) {
            e.printStackTrace()
            // 오류 발생 시 기본값으로 생성
            AuthViewModel(AuthRepositoryImpl(context))
        }
    }
    val chatViewModel = remember {
        ChatViewModel(ChatRepositoryImpl())
    }
    val chatHistoryRepository = remember {
        ChatHistoryRepositoryImpl(context) as com.banya.neulpum.domain.repository.ChatHistoryRepository
    }
    
    // 최근 채팅 목록 상태
    var recentConversations by remember { mutableStateOf(listOf<Conversation>()) }
    var currentConversationId by remember { mutableStateOf<String?>(null) }
    var isLoadingConversations by remember { mutableStateOf(false) }
    
    val authState = authViewModel.authState
    val currentUser = authViewModel.currentUser
    val isLoading = authViewModel.isLoading
    
    // 앱 시작 시 로그인 상태 확인
    LaunchedEffect(Unit) {
        try {
            authViewModel.checkLoginStatus()
        } catch (e: Exception) {
            e.printStackTrace()
            // 오류 발생 시에도 앱이 계속 실행되도록 함
        }
    }
    
    suspend fun refreshRecent(apiKey: String, setLoading: (Boolean) -> Unit = {}) {
        setLoading(true)
        chatHistoryRepository.getRecentConversations(apiKey, 30).fold(
            onSuccess = { conversations ->
                recentConversations = conversations
                setLoading(false)
            },
            onFailure = { error ->
                println("Failed to load conversations: ${error.message}")
                setLoading(false)
            }
        )
    }

    // 로그인 후 채팅 히스토리 로드
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("organization_api_key", "") ?: ""
            scope.launch { refreshRecent(apiKey) { isLoadingConversations = it } }
        }
    }
    
    // 로그인되지 않은 경우 로그인 화면으로 이동
    if (authState == com.banya.neulpum.presentation.viewmodel.AuthState.Unauthenticated) {
        // LoginActivity로 이동
        return
    }
    
    // 로딩 중인 경우
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF10A37F))
        }
        return
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = Color.White
            ) {
                // Drawer 고정 헤더/푸터 + 최근 채팅만 스크롤
                Column(modifier = Modifier.fillMaxHeight()) {
                    // 헤더
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10A37F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "늘품",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { scope.launch { drawerState.close() } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 상단 메뉴 (고정)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        DrawerMenuItem(
                            icon = Icons.Default.Mic,
                            label = "보이스 채팅",
                            subtitle = "음성으로 AI와 대화",
                            onClick = {
                                selectedScreen = "voice"
                                scope.launch { drawerState.close() }
                            },
                            isSelected = selectedScreen == "voice"
                        )
                        DrawerMenuItem(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            label = "일반 채팅",
                            subtitle = "채팅으로 AI와 대화",
                            onClick = {
                                selectedScreen = "chat"
                                currentConversationId = null
                                chatViewModel?.clearChat()
                                scope.launch { drawerState.close() }
                            },
                            isSelected = selectedScreen == "chat"
                        )
                    }

                    // 최근 채팅 (스크롤 영역)
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        item {
                            Text(
                                text = "최근 채팅",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        if (isLoadingConversations) {
                            item {
                                Text(
                                    text = "로딩 중...",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(recentConversations.size) { index ->
                                val conversation = recentConversations[index]
                                DrawerChatItem(
                                    title = conversation.title,
                                    onClick = {
                                        currentConversationId = conversation.id
                                        selectedScreen = if (conversation.type == "voice") "voice" else "chat"
                                        scope.launch { drawerState.close() }
                                    },
                                    isVoiceChat = conversation.type == "voice"
                                )
                            }
                            if (recentConversations.isEmpty()) {
                                item {
                                    Text(
                                        text = "아직 채팅이 없습니다",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 하단 프로필 (고정)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .clickable {
                                selectedScreen = "settings"
                                scope.launch { drawerState.close() }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    currentUser?.name ?: "사용자",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                                Text(
                                    currentUser?.email ?: "user@example.com",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "설정",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // 현재 채팅의 제목을 표시 (없으면 기본값)
                        val currentConversationTitle = remember(recentConversations, currentConversationId) {
                            recentConversations.firstOrNull { it.id == currentConversationId }?.title ?: "일반 채팅"
                        }
                        Text(
                            when (selectedScreen) {
                                "chat" -> currentConversationTitle
                                "voice" -> "보이스 채팅"
                                "settings" -> "설정"
                                else -> currentConversationTitle
                            },
                            color = Color.Black
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "메뉴",
                                tint = Color.Black
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color.Black,
                        navigationIconContentColor = Color.Black
                    )
                )
            }
        ) { paddingValues ->
            when (selectedScreen) {
                "chat" -> androidx.compose.runtime.key(currentConversationId) {
                    ChatScreen(
                        paddingValues = paddingValues, 
                        chatViewModel = chatViewModel, 
                        currentConversationId = currentConversationId,
                        onMessageSent = { message ->
                            // 메시지 전송 후 최근 채팅 목록 새로고침
                            scope.launch {
                                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                                val apiKey = prefs.getString("organization_api_key", "") ?: ""
                                if (apiKey.isNotEmpty()) refreshRecent(apiKey) {}
                            }
                        },
                        onConversationCreated = { _ ->
                            // 새로운 대화가 생성되면 즉시 최근 채팅 목록 새로고침
                            scope.launch {
                                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                                val apiKey = prefs.getString("organization_api_key", "") ?: ""
                                refreshRecent(apiKey) {}
                            }
                        },
                        chatHistoryRepository = chatHistoryRepository,
                        apiKey = {
                            val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.getString("organization_api_key", "") ?: ""
                        }()
                    )
                }
                "voice" -> VoiceChatScreen(
                    paddingValues = paddingValues,
                    conversationId = currentConversationId, // 일반 채팅과 동일하게 currentConversationId 전달
                    onConversationCreated = { newConversationId ->
                        currentConversationId = newConversationId
                        // 보이스 채팅 대화 생성 후 최근 채팅 목록 새로고침
                        scope.launch {
                            val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                            val apiKey = prefs.getString("organization_api_key", "") ?: ""
                            if (apiKey.isNotEmpty()) refreshRecent(apiKey) {}
                        }
                    }
                )
                "settings" -> SettingsScreen(
                    paddingValues = paddingValues,
                    onNavigateToOrganization = { showOrganizationScreen = true },
                    onNavigateToOrganizationCreate = { },
                    onNavigateToProfileEdit = { showProfileEditScreen = true }
                )
                else -> androidx.compose.runtime.key(currentConversationId) {
                    ChatScreen(
                        paddingValues = paddingValues, 
                        chatViewModel = chatViewModel, 
                        currentConversationId = currentConversationId,
                        onMessageSent = { message ->
                            // 메시지 전송 후 최근 채팅 목록 새로고침
                            scope.launch {
                                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                                val apiKey = prefs.getString("organization_api_key", "") ?: ""
                                if (apiKey.isNotEmpty()) refreshRecent(apiKey) {}
                            }
                        },
                        onConversationCreated = { _ ->
                            scope.launch {
                                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                                val apiKey = prefs.getString("organization_api_key", "") ?: ""
                                refreshRecent(apiKey) {}
                            }
                        },
                        chatHistoryRepository = chatHistoryRepository,
                        apiKey = {
                            val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.getString("organization_api_key", "") ?: ""
                        }()
                    )
                }
            }
        }
        
        // 기관 관리 화면
        if (showOrganizationScreen) {
            OrganizationScreen(
                onBack = { showOrganizationScreen = false },
                authViewModel = authViewModel
            )
        }
        
        // 프로필 수정 화면
        if (showProfileEditScreen) {
            ProfileEditScreen(
                onBack = { showProfileEditScreen = false },
                authViewModel = authViewModel
            )
        }
        
    }
}