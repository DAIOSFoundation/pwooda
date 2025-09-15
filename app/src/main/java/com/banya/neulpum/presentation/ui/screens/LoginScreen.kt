package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banya.neulpum.presentation.viewmodel.AuthState
import com.banya.neulpum.presentation.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    var isSignupMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var agreeToTerms by remember { mutableStateOf(false) }
    var agreeToPrivacy by remember { mutableStateOf(false) }
    var showTermsScreen by remember { mutableStateOf(false) }
    var showPrivacyScreen by remember { mutableStateOf(false) }
    var emailAvailable by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingEmail by remember { mutableStateOf(false) }
    var isEmailVerified by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var isSendingVerification by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var showVerificationCode by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    
    val authState = authViewModel.authState
    val isLoading = authViewModel.isLoading
    val errorMessage = authViewModel.errorMessage
    val scope = rememberCoroutineScope()
    
    // 로그인 성공 시 콜백 호출
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }
    
    // 에러 메시지 자동 제거
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(5000)
            authViewModel.clearError()
        }
    }
    
    // 이메일 중복 확인 (회원가입 모드에서만)
    LaunchedEffect(email, isSignupMode) {
        if (isSignupMode && email.isNotEmpty() && email.contains("@")) {
            isCheckingEmail = true
            emailAvailable = null
            // 이메일이 변경되면 인증 상태 초기화
            isEmailVerified = false
            showVerificationCode = false
            verificationCode = ""
            try {
                val result = authViewModel.checkEmail(email)
                if (result.isSuccess) {
                    emailAvailable = result.getOrNull() ?: false
                } else {
                    emailAvailable = null
                }
            } catch (e: Exception) {
                emailAvailable = null
            } finally {
                isCheckingEmail = false
            }
        } else {
            emailAvailable = null
            isEmailVerified = false
            showVerificationCode = false
            verificationCode = ""
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 로고 및 제목
        Text(
            text = "늘품",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF10A37F),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        
        // 모드 전환 버튼
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF7F7F8)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { isSignupMode = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isSignupMode) Color(0xFF10A37F) else Color.Transparent
                    ),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Text(
                        text = "로그인",
                        color = if (!isSignupMode) Color.White else Color.Black
                    )
                }
                
                Button(
                    onClick = { isSignupMode = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSignupMode) Color(0xFF10A37F) else Color.Transparent
                    ),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Text(
                        text = "회원가입",
                        color = if (isSignupMode) Color.White else Color.Black
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 입력 폼
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 이메일 입력
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("이메일", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "이메일",
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (isSignupMode) {
                            when {
                                isCheckingEmail -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Gray,
                                    strokeWidth = 2.dp
                                )
                                emailAvailable == true -> Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "사용 가능",
                                    tint = Color(0xFF10A37F)
                                )
                                emailAvailable == false -> Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "사용 불가",
                                    tint = Color(0xFFEA4335)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isSignupMode && emailAvailable == false) Color(0xFFEA4335) else Color(0xFF10A37F),
                        unfocusedBorderColor = if (isSignupMode && emailAvailable == false) Color(0xFFEA4335) else Color.LightGray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color(0xFF10A37F)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (isSignupMode) ImeAction.Next else ImeAction.Done
                    )
                )
                
                // 이메일 중복 확인 메시지 (회원가입 모드에서만)
                if (isSignupMode && email.isNotEmpty() && email.contains("@")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        isCheckingEmail -> Text(
                            text = "이메일 확인 중...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        emailAvailable == true -> Text(
                            text = "사용 가능한 이메일입니다",
                            color = Color(0xFF10A37F),
                            fontSize = 12.sp
                        )
                        emailAvailable == false -> Text(
                            text = "이미 사용 중인 이메일입니다",
                            color = Color(0xFFEA4335),
                            fontSize = 12.sp
                        )
                    }
                    
                    // 이메일 인증 섹션
                    if (emailAvailable == true) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (!isEmailVerified) {
                            // 인증번호 발송 버튼
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isSendingVerification = true
                                        try {
                                            val result = authViewModel.sendVerificationEmail(email)
                                            if (result.isSuccess) {
                                                showVerificationCode = true
                                            }
                                        } catch (e: Exception) {
                                            // 에러 처리
                                        } finally {
                                            isSendingVerification = false
                                        }
                                    }
                                },
                                enabled = !isSendingVerification,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF10A37F)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10A37F)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isSendingVerification) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF10A37F),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isSendingVerification) "인증번호 발송 중..." else "이메일 인증번호 발송",
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            // 인증 완료 표시
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "인증 완료",
                                    tint = Color(0xFF10A37F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "이메일 인증이 완료되었습니다",
                                    color = Color(0xFF10A37F),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        // 인증번호 입력 필드
                        if (showVerificationCode && !isEmailVerified) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                OutlinedTextField(
                                    value = verificationCode,
                                    onValueChange = { 
                                        if (it.length <= 6) {
                                            verificationCode = it
                                        }
                                    },
                                    label = { Text("인증번호 6자리", color = Color.Gray) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF10A37F),
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        cursorColor = Color(0xFF10A37F)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    visualTransformation = if (verificationCode.length == 6) {
                                        VisualTransformation.None
                                    } else {
                                        VisualTransformation.None
                                    }
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isVerifying = true
                                            try {
                                                val result = authViewModel.verifyEmail(email, verificationCode)
                                                if (result.isSuccess && result.getOrNull() == true) {
                                                    isEmailVerified = true
                                                    showVerificationCode = false
                                                    verificationCode = "" // 인증번호 필드 초기화
                                                } else {
                                                    // 인증 실패 시 스낵바 표시
                                                    showSnackbar = true
                                                    snackbarMessage = "인증번호가 올바르지 않습니다. 다시 확인해주세요."
                                                }
                                            } catch (e: Exception) {
                                                // 에러 처리
                                                showSnackbar = true
                                                snackbarMessage = "인증 중 오류가 발생했습니다. 다시 시도해주세요."
                                            } finally {
                                                isVerifying = false
                                            }
                                        }
                                    },
                                    enabled = verificationCode.length == 6 && !isVerifying,
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10A37F),
                                        disabledContainerColor = Color.LightGray
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isVerifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = "확인",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                text = "인증번호를 입력하고 확인 버튼을 눌러주세요 (5분 후 만료)",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isSignupMode) {
                    // 닉네임 입력 (회원가입 모드)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("닉네임", color = Color.Gray) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "닉네임",
                                tint = Color.Gray
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            cursorColor = Color(0xFF10A37F)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 비밀번호 입력
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "비밀번호",
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword }
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "비밀번호 숨기기" else "비밀번호 보기",
                                tint = Color.Gray
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10A37F),
                        unfocusedBorderColor = Color.LightGray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color(0xFF10A37F)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // 회원가입 모드일 때 비밀번호 확인
                if (isSignupMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("비밀번호 확인", color = Color.Gray) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "비밀번호 확인",
                                tint = Color.Gray
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showConfirmPassword = !showConfirmPassword }
                            ) {
                                Icon(
                                    imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showConfirmPassword) "비밀번호 숨기기" else "비밀번호 보기",
                                    tint = Color.Gray
                                )
                            }
                        },
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            cursorColor = Color(0xFF10A37F)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    )

                    val passwordMismatch = confirmPassword.isNotEmpty() && confirmPassword != password
                    if (passwordMismatch) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "비밀번호가 일치하지 않습니다.",
                            color = Color(0xFFEA4335),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 회원가입 모드일 때 동의 체크박스들
                if (isSignupMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 이용약관 동의
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = agreeToTerms,
                            onCheckedChange = { agreeToTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF10A37F),
                                uncheckedColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "이용약관에 동의합니다",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { showTermsScreen = true }
                ) {
                    Text(
                        text = "보기",
                        color = Color(0xFF10A37F),
                        fontSize = 12.sp
                    )
                }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 개인정보처리방침 동의
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = agreeToPrivacy,
                            onCheckedChange = { agreeToPrivacy = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF10A37F),
                                uncheckedColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "개인정보처리방침에 동의합니다",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { showPrivacyScreen = true }
                ) {
                    Text(
                        text = "보기",
                        color = Color(0xFF10A37F),
                        fontSize = 12.sp
                    )
                }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 에러 메시지
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFEA4335).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFEA4335),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 로그인/회원가입 버튼
                Button(
                    onClick = {
                        if (isSignupMode) {
                            if (password.isNotEmpty() && password == confirmPassword && name.isNotEmpty()) {
                                authViewModel.signup(email, password, name)
                            }
                        } else {
                            authViewModel.login(email, password)
                        }
                    },
                    enabled = run {
                        val basicConditions = !isLoading && email.isNotEmpty() && password.isNotEmpty()
                        val signupConditions = if (isSignupMode) {
                            name.isNotEmpty() && confirmPassword.isNotEmpty() && confirmPassword == password && agreeToTerms && agreeToPrivacy && emailAvailable == true && isEmailVerified
                        } else {
                            true
                        }
                        val isEnabled = basicConditions && signupConditions
                        
                        // 디버깅을 위한 로그
                        if (isSignupMode) {
                            println("회원가입 버튼 활성화 조건:")
                            println("  - isLoading: $isLoading")
                            println("  - email.isNotEmpty(): ${email.isNotEmpty()}")
                            println("  - password.isNotEmpty(): ${password.isNotEmpty()}")
                            println("  - name.isNotEmpty(): ${name.isNotEmpty()}")
                            println("  - confirmPassword.isNotEmpty(): ${confirmPassword.isNotEmpty()}")
                            println("  - confirmPassword == password: ${confirmPassword == password}")
                            println("  - agreeToTerms: $agreeToTerms")
                            println("  - agreeToPrivacy: $agreeToPrivacy")
                            println("  - emailAvailable == true: ${emailAvailable == true}")
                            println("  - isEmailVerified: $isEmailVerified")
                            println("  - 최종 활성화: $isEnabled")
                        }
                        
                        isEnabled
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10A37F)
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = if (isSignupMode) "회원가입" else "로그인",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 안내 텍스트
        Text(
            text = if (isSignupMode) {
                "이미 계정이 있으신가요? 로그인 탭을 클릭하세요"
            } else {
                "계정이 없으신가요? 회원가입 탭을 클릭하세요"
            },
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
    
    // 이용약관 스크린
    if (showTermsScreen) {
        TermsOfServiceScreen(
            onBack = { showTermsScreen = false }
        )
    }
    
    // 개인정보처리방침 스크린
    if (showPrivacyScreen) {
        PrivacyPolicyScreen(
            onBack = { showPrivacyScreen = false }
        )
    }
    
    // 스낵바 표시
    if (showSnackbar) {
        LaunchedEffect(showSnackbar) {
            kotlinx.coroutines.delay(3000) // 3초 후 자동 숨김
            showSnackbar = false
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEA4335)),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "에러",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = snackbarMessage,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showSnackbar = false }
                    ) {
                        Text(
                            text = "닫기",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
