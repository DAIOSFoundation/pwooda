package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import com.banya.neulpum.presentation.ui.components.CommonSnackbar
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val currentUser = authViewModel.currentUser
    val scrollState = rememberScrollState()
    
    // 태블릿에서는 최대 너비 제한
    val maxContentWidth = when {
        screenWidth > 1200.dp -> 800.dp  // 큰 태블릿
        screenWidth > 800.dp -> 700.dp    // 중간 태블릿
        else -> screenWidth               // 핸드폰
    }
    
    var name by remember(currentUser?.name) { mutableStateOf(currentUser?.name ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // 유효성 검사
    val isNameValid = name.isNotBlank() && name.length >= 2
    val isFormValid = isNameValid
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "프로필 수정",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
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
                    .padding(bottom = 80.dp), // 저장 버튼 공간 확보
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 이메일 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "이메일",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = currentUser?.email ?: "",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE9ECEF), RoundedCornerShape(8.dp))
                            .padding(18.dp)
                    )
                }
                
                // 닉네임 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "닉네임",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("닉네임을 입력하세요", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    if (name.isNotBlank() && !isNameValid) {
                        Text(
                            text = "닉네임은 2자 이상 입력해주세요",
                            fontSize = 12.sp,
                            color = Color.Red
                        )
                    }
                }
            }
            
            // 저장 버튼 (하단 고정)
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val result = authViewModel.updateProfile(
                                name = if (name != currentUser?.name) name else null,
                                currentPassword = null,
                                newPassword = null
                            )
                            
                            if (result.isSuccess) {
                                snackbarMessage = "프로필이 수정되었습니다"
                                showSnackbar = true
                                // 성공 시 화면 닫기
                                kotlinx.coroutines.delay(500)
                                onBack()
                            } else {
                                snackbarMessage = result.exceptionOrNull()?.message ?: "프로필 수정에 실패했습니다"
                                showSnackbar = true
                            }
                        } catch (e: Exception) {
                            snackbarMessage = e.message ?: "프로필 수정에 실패했습니다"
                            showSnackbar = true
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 15.dp)
                    .height(56.dp),
                enabled = isFormValid && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10A37F),
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
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
    
    // Snackbar
    CommonSnackbar(
        message = snackbarMessage,
        showSnackbar = showSnackbar,
        onDismiss = { showSnackbar = false }
    )
}
