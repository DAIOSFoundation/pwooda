package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.rememberCoroutineScope
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import com.banya.neulpum.presentation.ui.components.CommonSnackbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPw by remember { mutableStateOf(false) }
    var showNewPw by remember { mutableStateOf(false) }
    var showConfirmPw by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // 태블릿에서는 최대 너비 제한
    val maxContentWidth = when {
        screenWidth > 1200.dp -> 800.dp  // 큰 태블릿
        screenWidth > 800.dp -> 700.dp    // 중간 태블릿
        else -> screenWidth               // 핸드폰
    }
    
    // 유효성 검사
    val isCurrentPasswordValid = currentPassword.isNotBlank()
    val isNewPasswordValid = newPassword.length >= 6
    val isConfirmPasswordValid = newPassword == confirmPassword && confirmPassword.isNotBlank()
    val isFormValid = isCurrentPasswordValid && isNewPasswordValid && isConfirmPasswordValid
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "비밀번호 변경",
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
                    .padding(bottom = 80.dp), // 버튼 공간 확보
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 안내 텍스트
                Text(
                    text = "비밀번호를 변경하려면 현재 비밀번호와 새 비밀번호를 입력해주세요.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                // 현재 비밀번호 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "현재 비밀번호",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        placeholder = { Text("현재 비밀번호를 입력하세요", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showCurrentPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showCurrentPw = !showCurrentPw }) {
                                Icon(
                                    imageVector = if (showCurrentPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showCurrentPw) "비밀번호 숨기기" else "비밀번호 보기",
                                    tint = Color.Gray
                                )
                            }
                        },
                        singleLine = true
                    )
                }
                
                // 새 비밀번호 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "새 비밀번호",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        placeholder = { Text("새 비밀번호를 입력하세요 (6자 이상)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showNewPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showNewPw = !showNewPw }) {
                                Icon(
                                    imageVector = if (showNewPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showNewPw) "비밀번호 숨기기" else "비밀번호 보기",
                                    tint = Color.Gray
                                )
                            }
                        },
                        singleLine = true
                    )
                    
                    if (newPassword.isNotBlank() && !isNewPasswordValid) {
                        Text(
                            text = "비밀번호는 6자 이상 입력해주세요",
                            fontSize = 12.sp,
                            color = Color.Red
                        )
                    }
                }
                
                // 새 비밀번호 확인 섹션
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "새 비밀번호 확인",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = { Text("새 비밀번호를 다시 입력하세요", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showConfirmPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPw = !showConfirmPw }) {
                                Icon(
                                    imageVector = if (showConfirmPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showConfirmPw) "비밀번호 숨기기" else "비밀번호 보기",
                                    tint = Color.Gray
                                )
                            }
                        },
                        singleLine = true
                    )
                    
                    if (confirmPassword.isNotBlank() && !isConfirmPasswordValid) {
                        Text(
                            text = "비밀번호가 일치하지 않습니다",
                            fontSize = 12.sp,
                            color = Color.Red
                        )
                    }
                }
            }
            
            // 비밀번호 변경 버튼 (하단 고정)
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val result = authViewModel.updateProfile(
                                name = null,
                                currentPassword = currentPassword,
                                newPassword = newPassword
                            )
                            
                            if (result.isSuccess) {
                                snackbarMessage = "비밀번호가 변경되었습니다"
                                showSnackbar = true
                                // 성공 시 화면 닫기
                                kotlinx.coroutines.delay(500)
                                onBack()
                            } else {
                                snackbarMessage = result.exceptionOrNull()?.message ?: "비밀번호 변경에 실패했습니다"
                                showSnackbar = true
                            }
                        } catch (e: Exception) {
                            snackbarMessage = e.message ?: "비밀번호 변경에 실패했습니다"
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
                        text = "비밀번호 변경",
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

