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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
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
import com.banya.neulpum.domain.entity.User
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
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
    
    // 화면 크기에 따른 패딩 계산
    val horizontalPadding = when {
        screenWidth > 1200.dp -> 120.dp  // 큰 테블릿
        screenWidth > 800.dp -> 80.dp    // 중간 테블릿
        else -> 24.dp                    // 핸드폰
    }
    
    var name by remember { mutableStateOf(currentUser?.name ?: "") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPw by remember { mutableStateOf(false) }
    var showNewPw by remember { mutableStateOf(false) }
    var showConfirmPw by remember { mutableStateOf(false) }
    var showPasswordSection by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // 유효성 검사
    val isNameValid = name.isNotBlank() && name.length >= 2
    val isCurrentPasswordValid = currentPassword.isNotBlank()
    val isNewPasswordValid = newPassword.length >= 6
    val isConfirmPasswordValid = newPassword == confirmPassword && confirmPassword.isNotBlank()
    
    // 닉네임만 변경하는 경우와 비밀번호 변경하는 경우를 구분
    val isChangingPassword = newPassword.isNotBlank()
    val isFormValid = if (isChangingPassword) {
        // 비밀번호 변경하는 경우: 모든 필드 필요
        isNameValid && isCurrentPasswordValid && isNewPasswordValid && isConfirmPasswordValid
    } else {
        // 닉네임만 변경하는 경우: 닉네임만 필요
        isNameValid
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 상단 앱바
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
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 24.dp)
        ) {
            // 프로필 정보 섹션
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF10A37F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "기본 정보",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10A37F)
                        )
                    }
                    
                    // 현재 이메일 (읽기 전용)
                    Text(
                        text = "이메일",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = currentUser?.email ?: "",
                        fontSize = 16.sp,
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE9ECEF), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 닉네임 입력
                    Text(
                        text = "닉네임",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
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
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    if (name.isNotBlank() && !isNameValid) {
                        Text(
                            text = "닉네임은 2자 이상 입력해주세요",
                            fontSize = 12.sp,
                            color = Color.Red,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 비밀번호 변경 섹션 (조건부 표시)
            if (showPasswordSection) {
                Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF10A37F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "비밀번호 변경",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10A37F)
                        )
                    }
                    
                    // 현재 비밀번호
                    Text(
                        text = "현재 비밀번호",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
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
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 새 비밀번호
                    Text(
                        text = "새 비밀번호",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        placeholder = { Text("새 비밀번호를 입력하세요", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
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
                            color = Color.Red,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 새 비밀번호 확인
                    Text(
                        text = "새 비밀번호 확인",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
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
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
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
                            color = Color.Red,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            } else {
                // 비밀번호 변경 버튼
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF10A37F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "비밀번호 변경",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10A37F)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        
                        Text(
                            text = "비밀번호를 변경하려면 아래 버튼을 눌러주세요",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        OutlinedButton(
                            onClick = { showPasswordSection = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF10A37F)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10A37F)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "비밀번호 변경하기",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 저장 버튼
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        try {
                            val result = authViewModel.updateProfile(
                                name = if (name != currentUser?.name) name else null,
                                currentPassword = if (newPassword.isNotBlank()) currentPassword else null,
                                newPassword = if (newPassword.isNotBlank()) newPassword else null
                            )
                            
                            if (result.isSuccess) {
                                snackbarMessage = "프로필이 수정되었습니다"
                                showSnackbar = true
                                // 성공 시 화면 닫기
                                kotlinx.coroutines.delay(1500)
                                onBack()
                            } else {
                                snackbarMessage = result.exceptionOrNull()?.message ?: "프로필 수정에 실패했습니다"
                                showSnackbar = true
                            }
                        } catch (e: Exception) {
                            snackbarMessage = e.message ?: "프로필 수정에 실패했습니다"
                            showSnackbar = true
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = isFormValid && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10A37F),
                    disabledContainerColor = Color.LightGray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "저장하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 취소 버튼
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF10A37F)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10A37F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "취소",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    
    // 스낵바
    if (showSnackbar) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            showSnackbar = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = snackbarMessage,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
