package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banya.neulpum.data.repository.AuthRepositoryImpl
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToTermsOfService: () -> Unit = {}
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepositoryImpl(context) }
    val authViewModel = remember { AuthViewModel(authRepository) }
    
    var isLoading by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    val currentUser = authViewModel.currentUser
    val authState = authViewModel.authState
    
    // 프로필 정보 자동 로드
    LaunchedEffect(Unit) {
        if (authState is com.banya.neulpum.presentation.viewmodel.AuthState.Authenticated) {
            authViewModel.getMyProfile()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
        // 상단 헤더
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF2D2D2D),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "프로필",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "사용자 정보 관리",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        
        // 프로필 정보 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 프로필 이미지 (기본 아이콘)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4285F4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "프로필",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 사용자 이름
                Text(
                    text = currentUser?.name ?: "사용자",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // 사용자 이메일
                Text(
                    text = currentUser?.email ?: "email@example.com",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 계정 생성일
                currentUser?.createdAt?.let { createdAt ->
                    Text(
                        text = "가입일: $createdAt",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
        
        // 계정 정보 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "계정 정보",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 사용자 ID
                ProfileInfoRow(
                    icon = Icons.Default.Fingerprint,
                    label = "사용자 ID",
                    value = currentUser?.id ?: "로딩 중..."
                )
                
                // 이메일
                ProfileInfoRow(
                    icon = Icons.Default.Email,
                    label = "이메일",
                    value = currentUser?.email ?: "로딩 중..."
                )
                
                // 이름
                ProfileInfoRow(
                    icon = Icons.Default.Person,
                    label = "이름",
                    value = currentUser?.name ?: "로딩 중..."
                )
                
                // 로그인 상태
                ProfileInfoRow(
                    icon = Icons.Default.CheckCircle,
                    label = "로그인 상태",
                    value = if (currentUser?.isLoggedIn == true) "로그인됨" else "로그아웃됨"
                )
            }
        }
        
        // 계정 관리 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "계정 관리",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 프로필 새로고침
                ProfileActionRow(
                    icon = Icons.Default.Refresh,
                    label = "프로필 새로고침",
                    onClick = {
                        isLoading = true
                        authViewModel.getMyProfile()
                        // 로딩 상태 시뮬레이션
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000)
                            isLoading = false
                        }
                    }
                )
                
                // 토큰 갱신
                ProfileActionRow(
                    icon = Icons.Default.Security,
                    label = "토큰 갱신",
                    onClick = {
                        isLoading = true
                        authViewModel.refreshToken()
                        // 로딩 상태 시뮬레이션
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000)
                            isLoading = false
                        }
                    }
                )
                
            }
        }
        
        // 법적 정보 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "법적 정보",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 개인정보처리방침
                ProfileActionRow(
                    icon = Icons.Default.PrivacyTip,
                    label = "개인정보처리방침",
                    onClick = onNavigateToPrivacyPolicy
                )
                
                // 이용약관
                ProfileActionRow(
                    icon = Icons.Default.Description,
                    label = "이용약관",
                    onClick = onNavigateToTermsOfService
                )
                
            }
        }
        
        // 로딩 인디케이터
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF4285F4)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        }
        
        // 하단 로그아웃 버튼
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 15.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2D2D2D)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                ProfileActionRow(
                    icon = Icons.Default.Logout,
                    label = "로그아웃",
                    onClick = { showLogoutDialog = true },
                    textColor = Color(0xFFEA4335)
                )
            }
        }
    }
    
    // 로그아웃 확인 다이얼로그
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃", color = Color.White) },
            text = { Text("정말 로그아웃하시겠습니까?", color = Color.Gray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.logout()
                        showLogoutDialog = false
                    }
                ) {
                    Text("로그아웃", color = Color(0xFFEA4335))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("취소", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF2D2D2D)
        )
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    textColor: Color = Color.White
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = label,
                fontSize = 14.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "이동",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
