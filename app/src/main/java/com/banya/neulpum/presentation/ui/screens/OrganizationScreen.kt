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
import com.banya.neulpum.domain.entity.Organization
import com.banya.neulpum.data.dto.OrganizationDto
import com.banya.neulpum.data.repository.OrganizationRepositoryImpl
import com.banya.neulpum.di.NetworkModule
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
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
    val scope = rememberCoroutineScope()
    val orgRepository = remember { OrganizationRepositoryImpl(context) }
    
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
            // 1) User 객체에서 기관 정보 확인
            val user = authViewModel.currentUser
            if (user?.organizationName != null && user.organizationApiKey != null) {
                currentOrganization = Organization(
                    id = "", // User 객체에는 id가 없으므로 빈 문자열
                    name = user.organizationName,
                    description = user.organizationDescription ?: "", // User 객체의 description 사용
                    apiKey = user.organizationApiKey,
                    isActive = true
                )
                isLoading = false
                return@LaunchedEffect
            }
            
            // 사용자의 현재 기관 정보는 AuthViewModel에서 이미 가져옴
        } catch (e: Exception) {
            // 에러 무시
        } finally {
            isLoading = false
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
                    currentOrganization = org.toOrganization()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    "기관 관리",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
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
                containerColor = Color.White,
                titleContentColor = Color.Black
            )
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 현재 기관 정보 표시 (기관이 있을 때만)
                if (currentOrganization != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Business,
                                    contentDescription = null,
                                    tint = Color(0xFF10A37F),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "현재 기관",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10A37F)
                                )
                            }
                            
                            Text(
                                "${currentOrganization!!.name}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            if (!currentOrganization!!.description.isNullOrBlank()) {
                                Text(
                                    "${currentOrganization!!.description}",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            
                            Text(
                                "API Key: ${currentOrganization!!.apiKey}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
                
                // 기관 검색/가입 섹션 (기관이 있든 없든 항상 표시)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF10A37F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "기관 검색",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10A37F)
                            )
                        }
                        
                        Text(
                            "기관 API 키를 입력하여 기관을 검색하고 가입하세요.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("기관 API 키", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10A37F),
                                unfocusedBorderColor = Color.LightGray,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { searchOrganization() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = apiKey.isNotBlank() && !isLookingUp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10A37F)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLookingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                "기관 검색",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // 검색된 기관 정보 표시
                        if (foundOrganization != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10A37F))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        "검색된 기관",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10A37F),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Text(
                                        "기관명: ${foundOrganization!!.name}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    
                                    if (!foundOrganization!!.description.isNullOrBlank()) {
                                        Text(
                                            "설명: ${foundOrganization!!.description}",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    
                                    Button(
                                        onClick = { joinOrganization() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isJoining,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF10A37F)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        if (isJoining) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            "기관 가입",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Snackbar
    if (showSnackbar) {
        LaunchedEffect(showSnackbar) {
            kotlinx.coroutines.delay(3000)
            showSnackbar = false
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))
            ) {
                Text(
                    text = snackbarMessage,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}