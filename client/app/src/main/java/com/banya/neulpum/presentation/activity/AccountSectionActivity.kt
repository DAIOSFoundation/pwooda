package com.banya.neulpum.presentation.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.banya.neulpum.data.repository.AuthRepositoryImpl
import com.banya.neulpum.presentation.ui.screens.AccountSectionScreen
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import com.banya.neulpum.presentation.activity.LoginActivity
import com.banya.neulpum.presentation.ui.components.CommonSnackbar
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

class AccountSectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true
        window.statusBarColor = android.graphics.Color.WHITE
        
        val authRepository = AuthRepositoryImpl(this)
        val authViewModel = AuthViewModel(authRepository)
        
        setContent {
            var showDeleteConfirm by remember { mutableStateOf(false) }
            var showSnackbar by remember { mutableStateOf(false) }
            var snackbarMessage by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            
            AccountSectionScreen(
                onBack = { finish() },
                onPasswordChange = {
                    val intent = Intent(this, ChangePasswordActivity::class.java)
                    startActivity(intent)
                },
                onDeleteAccount = {
                    showDeleteConfirm = true
                }
            )
            
            // 회원 탈퇴 확인 다이얼로그
            if (showDeleteConfirm) {
                DeleteAccountDialog(
                    onDismiss = { 
                        if (!showDeleteConfirm) return@DeleteAccountDialog
                        showDeleteConfirm = false 
                    },
                    onConfirm = { password, setIsLoading ->
                        scope.launch {
                            setIsLoading(true)
                            try {
                                val result = authViewModel.deleteAccount(password)
                                if (result.isSuccess) {
                                    showDeleteConfirm = false
                                    // 로그인 화면으로 이동
                                    authViewModel.logout()
                                    val intent = Intent(this@AccountSectionActivity, LoginActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    setIsLoading(false)
                                    val errorMessage = result.exceptionOrNull()?.message ?: "회원 탈퇴에 실패했습니다"
                                    snackbarMessage = errorMessage
                                    showSnackbar = true
                                }
                            } catch (e: Exception) {
                                setIsLoading(false)
                                snackbarMessage = e.message ?: "회원 탈퇴 중 오류가 발생했습니다"
                                showSnackbar = true
                            }
                        }
                    }
                )
            }
            
            // Snackbar
            CommonSnackbar(
                message = snackbarMessage,
                showSnackbar = showSnackbar,
                onDismiss = { showSnackbar = false }
            )
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, (Boolean) -> Unit) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { 
            Text(
                "회원 탈퇴", 
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column {
                Text(
                    "정말 탈퇴하시겠어요? 이 작업은 되돌릴 수 없습니다.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (!isLoading) password = it },
                    label = { Text("비밀번호 확인", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEA4335),
                        unfocusedBorderColor = Color.LightGray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    ),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "비밀번호 숨기기" else "비밀번호 보기",
                                tint = Color.Gray
                            )
                        }
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFFEA4335)
                )
            } else {
                TextButton(
                    onClick = { 
                        if (password.isNotBlank()) {
                            onConfirm(password) { loading ->
                                isLoading = loading
                            }
                        }
                    },
                    enabled = password.isNotBlank() && !isLoading
                ) { 
                    Text(
                        "탈퇴", 
                        color = Color(0xFFEA4335),
                        fontWeight = FontWeight.Bold
                    ) 
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) { 
                Text("취소", color = Color.Gray) 
            }
        },
        containerColor = Color.White
    )
}

