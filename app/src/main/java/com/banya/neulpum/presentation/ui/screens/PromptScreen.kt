package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banya.neulpum.data.datasource.OrganizationRemoteDataSource
import com.banya.neulpum.data.remote.OrganizationApiService
import com.banya.neulpum.di.NetworkModule
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val currentUser = authViewModel.currentUser
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    var userPrompt by remember { mutableStateOf("") }
    var assistantPrompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    
    val apiService: OrganizationApiService = NetworkModule.provideOrganizationApiService()
    val dataSource = OrganizationRemoteDataSource(apiService)
    
    // 프롬프트 로드
    LaunchedEffect(Unit) {
        if (currentUser?.id != null) {
            isLoading = true
            try {
                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)
                val authToken = if (accessToken != null) "Bearer $accessToken" else null
                
                val response = dataSource.getMemberPrompts(currentUser.id, authToken)
                if (response.isSuccessful) {
                    val prompts = response.body()
                    if (prompts != null) {
                        userPrompt = prompts["user_prompt"] ?: ""
                        assistantPrompt = prompts["assistant_prompt"] ?: ""
                    }
                } else {
                    snackbarMessage = "프롬프트를 불러오는데 실패했습니다."
                    showSnackbar = true
                }
            } catch (e: Exception) {
                snackbarMessage = "오류가 발생했습니다: ${e.message}"
                showSnackbar = true
            } finally {
                isLoading = false
            }
        }
    }
    
    // 저장 함수
    fun savePrompts() {
        if (currentUser?.id == null) return
        
        scope.launch {
            isSaving = true
            try {
                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                val accessToken = prefs.getString("access_token", null)
                val authToken = if (accessToken != null) "Bearer $accessToken" else null
                
                val response = dataSource.saveMemberPrompts(
                    currentUser.id,
                    userPrompt,
                    assistantPrompt,
                    authToken
                )
                
                if (response.isSuccessful) {
                    snackbarMessage = "저장되었습니다."
                    showSnackbar = true
                    // 저장 성공 후 설정 화면으로 돌아가기
                    kotlinx.coroutines.delay(500)
                    onBack()
                } else {
                    snackbarMessage = "저장에 실패했습니다."
                    showSnackbar = true
                }
            } catch (e: Exception) {
                snackbarMessage = "오류가 발생했습니다: ${e.message}"
                showSnackbar = true
            } finally {
                isSaving = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "개인화",
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
        // 내용
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF10A37F))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState, enabled = true)
                        .padding(24.dp)
                        .padding(bottom = 80.dp), // 저장 버튼 공간 확보
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                // 유저 프롬프트 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "유저 프롬프트",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "사용자에 추가될 프롬프트입니다.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = userPrompt,
                        onValueChange = { userPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    // 포커스 시 스크롤하여 저장 버튼이 키보드 위에 보이도록
                                    scope.launch {
                                        kotlinx.coroutines.delay(500) // 키보드가 올라올 시간 대기
                                        // 저장 버튼 위치로 스크롤 (최대값으로 스크롤)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            },
                        placeholder = { Text("유저 프롬프트를 입력하세요", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 10,
                        minLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                    )
                }
                
                // 어시스턴트 프롬프트 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "어시스턴트 프롬프트",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "어시스턴트에 추가될 프롬프트입니다.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = assistantPrompt,
                        onValueChange = { assistantPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    // 포커스 시 스크롤하여 저장 버튼이 키보드 위에 보이도록
                                    scope.launch {
                                        kotlinx.coroutines.delay(500) // 키보드가 올라올 시간 대기
                                        // 저장 버튼 위치로 스크롤 (최대값으로 스크롤)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            },
                        placeholder = { Text("어시스턴트 프롬프트를 입력하세요", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 10,
                        minLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                    )
                }
                }
                
                // 저장 버튼 (하단 고정)
                Button(
                    onClick = { savePrompts() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 15.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10A37F),
                        disabledContainerColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "저장",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
    
    // Snackbar (하단에 표시)
    if (showSnackbar) {
        LaunchedEffect(showSnackbar) {
            kotlinx.coroutines.delay(3000)
            showSnackbar = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp),
                color = Color(0xFF10A37F),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        snackbarMessage, 
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

