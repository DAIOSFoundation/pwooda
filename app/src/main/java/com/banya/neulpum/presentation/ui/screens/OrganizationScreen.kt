package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.imePadding
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.banya.neulpum.domain.entity.Organization
import com.banya.neulpum.data.dto.OrganizationDto
import com.banya.neulpum.data.repository.OrganizationRepositoryImpl
import com.banya.neulpum.di.NetworkModule
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import com.banya.neulpum.presentation.ui.components.CommonSnackbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel
) {
    var apiKey by remember { mutableStateOf("") }
    var foundOrganization by remember { mutableStateOf<OrganizationDto?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    var currentOrganization by remember { mutableStateOf<Organization?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val scope = rememberCoroutineScope()
    val orgRepository = remember { OrganizationRepositoryImpl(context) }
    val scrollState = rememberScrollState()
    
    // 태블릿에서는 최대 너비 제한
    val maxContentWidth = when {
        screenWidth > 1200.dp -> 800.dp  // 큰 태블릿
        screenWidth > 800.dp -> 700.dp    // 중간 태블릿
        else -> screenWidth               // 핸드폰
    }
    
    // OrganizationDto를 Organization으로 변환하는 함수
    fun OrganizationDto.toOrganization(): Organization {
        return Organization(
            id = this.id,
            name = this.name,
            description = this.description,
            apiKey = this.api_key,
            isActive = this.is_active
        )
    }
    
    // 현재 기관 정보 로드
    LaunchedEffect(Unit) {
        try {
            // 최신 사용자 정보 가져오기
            authViewModel.getMyProfile()
            
            val user = authViewModel.currentUser
            if (user?.organizationName != null && user.organizationApiKey != null) {
                currentOrganization = Organization(
                    id = "", // User 객체에는 id가 없으므로 빈 문자열
                    name = user.organizationName,
                    description = user.organizationDescription ?: "",
                    apiKey = user.organizationApiKey,
                    isActive = true
                )
            } else {
                currentOrganization = null
            }
        } catch (e: Exception) {
            currentOrganization = null
        } finally {
            isLoading = false
        }
    }
    
    // currentUser가 변경될 때마다 기관 정보 업데이트
    LaunchedEffect(authViewModel.currentUser) {
        val user = authViewModel.currentUser
        if (user?.organizationName != null && user.organizationApiKey != null) {
            currentOrganization = Organization(
                id = "",
                name = user.organizationName,
                description = user.organizationDescription ?: "",
                apiKey = user.organizationApiKey,
                isActive = true
            )
        } else {
            currentOrganization = null
        }
    }
    
    // 기관 검색 함수
    fun searchOrganization() {
        if (apiKey.isBlank()) return
        
        scope.launch {
            isLookingUp = true
            try {
                val response = orgRepository.lookupByApiKey(apiKey)
                if (response.isSuccessful && response.body() != null) {
                    foundOrganization = response.body()
                    snackbarMessage = "기관을 찾았습니다"
                    showSnackbar = true
                } else {
                    foundOrganization = null
                    snackbarMessage = "기관을 찾을 수 없습니다"
                    showSnackbar = true
                }
            } catch (e: Exception) {
                foundOrganization = null
                snackbarMessage = "검색 중 오류가 발생했습니다"
                showSnackbar = true
            } finally {
                isLookingUp = false
            }
        }
    }
    
    // 기관 가입 함수
    fun joinOrganization() {
        if (foundOrganization == null) return
        
        scope.launch {
            isJoining = true
            try {
                val org = foundOrganization!!
                val response = orgRepository.joinByApiKey(org.api_key)
                if (response.isSuccessful) {
                    snackbarMessage = "기관 가입이 완료되었습니다"
                    showSnackbar = true
                    // 최신 사용자 정보 가져오기
                    authViewModel.getMyProfile()
                    // currentOrganization은 LaunchedEffect에서 자동으로 업데이트됨
                    foundOrganization = null
                    apiKey = ""
                } else {
                    snackbarMessage = "가입에 실패했습니다"
                    showSnackbar = true
                }
            } catch (e: Exception) {
                snackbarMessage = "가입 중 오류가 발생했습니다"
                showSnackbar = true
            } finally {
                isJoining = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "기관 관리",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                        .align(Alignment.TopCenter)
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(24.dp)
                        .padding(bottom = 80.dp), // 버튼 공간 확보
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 현재 기관 정보 표시 (기관이 있을 때만)
                    if (currentOrganization != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "현재 기관",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            
                            Text(
                                currentOrganization!!.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                            
                            if (!currentOrganization!!.description.isNullOrBlank()) {
                                Text(
                                    currentOrganization!!.description,
                                    fontSize = 15.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "API Key ${currentOrganization!!.apiKey}",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("API Key", currentOrganization!!.apiKey)
                                        clipboard.setPrimaryClip(clip)
                                        snackbarMessage = "API Key가 복사되었습니다"
                                        showSnackbar = true
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "복사",
                                        tint = Color(0xFF10A37F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // 기관 검색/가입 섹션
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "기관 검색",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        
                        Text(
                            "기관 API 키를 입력하여 기관을 검색하고 가입하세요.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = { Text("기관 API 키", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10A37F),
                                unfocusedBorderColor = Color.LightGray,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        // 검색된 기관 정보 표시
                        if (foundOrganization != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "검색된 기관",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10A37F)
                                )
                                
                                Text(
                                    "기관명: ${foundOrganization!!.name}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                                
                                if (!foundOrganization!!.description.isNullOrBlank()) {
                                    Text(
                                        "설명: ${foundOrganization!!.description}",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                Button(
                                    onClick = { joinOrganization() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled = !isJoining,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10A37F),
                                        disabledContainerColor = Color.Gray
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isJoining) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            "기관 가입",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 기관 검색 버튼 (하단 고정)
                Button(
                    onClick = { searchOrganization() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 15.dp)
                        .height(56.dp),
                    enabled = apiKey.isNotBlank() && !isLookingUp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10A37F),
                        disabledContainerColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLookingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "기관 검색",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    
    // Snackbar
    CommonSnackbar(
        message = snackbarMessage,
        showSnackbar = showSnackbar,
        onDismiss = { showSnackbar = false }
    )
}