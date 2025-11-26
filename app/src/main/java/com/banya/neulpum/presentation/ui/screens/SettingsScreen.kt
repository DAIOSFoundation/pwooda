package com.banya.neulpum.presentation.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Intent
import com.banya.neulpum.data.repository.AuthRepositoryImpl
import com.banya.neulpum.data.repository.SettingsRepositoryImpl
import com.banya.neulpum.domain.entity.*
import com.banya.neulpum.domain.entity.Organization
import com.banya.neulpum.presentation.theme.ThemeManager
import com.banya.neulpum.presentation.activity.LoginActivity
import com.banya.neulpum.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.banya.neulpum.presentation.ui.components.settings.InfoBadge
import com.banya.neulpum.presentation.ui.components.settings.SettingListRow
import com.banya.neulpum.presentation.ui.components.settings.SettingsActionItem
import com.banya.neulpum.presentation.ui.components.settings.SettingsDropdownItem
import com.banya.neulpum.presentation.ui.components.settings.SettingsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    onNavigateToOrganization: () -> Unit = {},
    onNavigateToOrganizationCreate: () -> Unit = {},
    onNavigateToProfileEdit: () -> Unit = {},
    onNavigateToAccountSection: () -> Unit = {},
    onNavigateToPrompt: () -> Unit = {},
    onNavigateToVoiceChatSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val authRepository = remember { AuthRepositoryImpl(context) }
    val settingsRepository = remember { SettingsRepositoryImpl(context) }
    val authViewModel = remember { AuthViewModel(authRepository) }
    val orgRepository = remember { com.banya.neulpum.data.repository.OrganizationRepositoryImpl(context) }
    
    // 태블릿에서는 최대 너비 제한
    val maxContentWidth = when {
        screenWidth > 1200.dp -> 1100.dp  // 큰 태블릿
        screenWidth > 800.dp -> 900.dp    // 중간 태블릿
        else -> 480.dp                    // 핸드폰
    }
    
    var currentSettings by remember { mutableStateOf<AppSettings?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAccountDeletionRequest by remember { mutableStateOf(false) }
    
    val currentUser = authViewModel.currentUser
    val scope = rememberCoroutineScope()
    
    // 설정 로드
    LaunchedEffect(Unit) {
        currentSettings = settingsRepository.getSettings()
        // 네트워크가 있을 때만 사용자 정보 최신화
        val isNetworkAvailable = com.banya.neulpum.utils.NetworkUtils.isNetworkAvailable(context)
        if (isNetworkAvailable) {
            authViewModel.getMyProfile()
        }
        // 네트워크가 없어도 저장된 사용자 정보로 화면 표시
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 상단 프로필 카드 (단순하고 깔끔한 구성)
        // 상단 프로필 카드 + 간단한 아바타 배지
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxContentWidth)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                 // 닉네임(이름)
                 Text(
                     text = currentUser?.name ?: "사용자",
                     fontSize = 20.sp,
                     fontWeight = FontWeight.Bold,
                     color = Color.Black,
                     textAlign = TextAlign.Center,
                     modifier = Modifier.fillMaxWidth()
                 )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 이메일
                Text(
                    text = currentUser?.email ?: "email@example.com",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // 프로필 수정 버튼 (닉네임/비밀번호 변경)
                Button(
                    onClick = { onNavigateToProfileEdit() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10A37F)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("프로필 수정", color = Color.White)
                }
                
                // 상단 카드에서는 계정 액션 제거 (하단 고정 영역으로 이동)
            }
        }
        // 조직 API-Key 가입/조회 섹션
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = maxContentWidth)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                        var orgName by remember { mutableStateOf<String?>(null) }
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(currentUser) {
                            // 1) Try user-attached organization name from profile
                            val userOrgName = currentUser?.organizationName
                            if (!userOrgName.isNullOrBlank()) {
                                orgName = userOrgName
                                return@LaunchedEffect
                            }
                            // 사용자의 기관 정보는 AuthViewModel에서 이미 가져옴
                        }
                        SettingListRow(
                            title = "기관",
                            trailingText = (orgName ?: "없음"),
                            showChevron = true,
                            onClick = { onNavigateToOrganization() }
                        )
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // 계정 섹션
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = maxContentWidth)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp)) {

                        SettingListRow(
                            title = "개인화",
                            onClick = { 
                                onNavigateToPrompt()
                            },
                            showChevron = true
                        )
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                        SettingListRow(
                            title = "음성채팅",
                            onClick = { 
                                onNavigateToVoiceChatSettings()
                            },
                            showChevron = true
                        )
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                                                SettingListRow(
                            title = "계정",
                            onClick = { 
                                onNavigateToAccountSection()
                            },
                            showChevron = true
                        )
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // 통합 리스트: 개인정보처리방침 / 이용약관 / 앱 정보(버전)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = maxContentWidth)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val ctx = LocalContext.current
                    val pm = ctx.packageManager
                    val pkg = ctx.packageName
                    val pInfo = try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(pkg, 0)
                        }
                    } catch (e: Exception) { null }
                    val versionName = pInfo?.versionName ?: "-"

                    Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                        SettingListRow(
                            title = "개인정보처리방침",
                            onClick = {
                                val intent = Intent(context, com.banya.neulpum.presentation.activity.PrivacyPolicyActivity::class.java)
                                context.startActivity(intent)
                            },
                            showChevron = true
                        )
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                        SettingListRow(
                            title = "이용약관",
                            onClick = {
                                val intent = Intent(context, com.banya.neulpum.presentation.activity.TermsOfServiceActivity::class.java)
                                context.startActivity(intent)
                            },
                            showChevron = true
                        )
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                        SettingListRow(
                            title = "앱 정보",
                            trailingText = "v$versionName",
                            onClick = {}
                        )
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }
        }

        }
        
        // 하단 로그아웃 버튼 (고정)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 15.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                    SettingListRow(
                        title = "로그아웃",
                        onClick = {
                            authViewModel.logout()
                            // 이동: 로그인 화면
                            val intent = Intent(context, LoginActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            // 현재 Activity 종료
                            (context as? Activity)?.finish()
                        },
                        centered = true
                    )
                }
            }
        }
    }
    
    
    // 회원 탈퇴 확인
    if (showDeleteConfirm) {
        DeleteAccountDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { password ->
                scope.launch {
                    try {
                        val result = authViewModel.deleteAccount(password)
                        if (result.isSuccess) {
                            showDeleteConfirm = false
                            // 로그인 화면으로 이동
                            authViewModel.logout()
                            val intent = Intent(context, LoginActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            // 현재 Activity 종료
                            (context as? Activity)?.finish()
                        } else {
                            // 에러 메시지 표시 (추가 구현 필요)
                        }
                    } catch (e: Exception) {
                        // 에러 처리
                    }
                }
            }
        )
    }

    // 계정 삭제 요청 화면
    if (showAccountDeletionRequest) {
        BackHandler {
            showAccountDeletionRequest = false
        }
        AccountDeletionRequestScreen(
            onBack = { showAccountDeletionRequest = false },
            authViewModel = authViewModel
        )
    }


    
    
 
 }
 
 @Composable
 private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF10A37F),
                checkedTrackColor = Color(0xFF10A37F).copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SettingsDropdownItem(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        
        Box {
            TextButton(
                onClick = { expanded = true }
            ) {
                Text(
                    text = options[selectedIndex],
                    color = Color(0xFF10A37F)
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.Black) },
                        onClick = {
                            onOptionSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    textColor: Color = Color.White
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF10A37F),
                modifier = Modifier.size(18.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "이동",
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun InfoBadge(text: String, muted: Boolean = false) {
    Surface(
        color = if (muted) Color(0xFFF4F6F8) else Color(0xFFE6F4F0),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            color = if (muted) Color(0xFF6B7280) else Color(0xFF0F9F74),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}


@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onValueChange = { password = it },
                    label = { Text("비밀번호 확인", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
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
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
            TextButton(
                onClick = { 
                    println("탈퇴 버튼 클릭됨, 비밀번호: '$password'")
                    if (password.isNotBlank()) {
                        println("onConfirm 호출 중...")
                        onConfirm(password)
                    } else {
                        println("비밀번호가 비어있음")
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

@Composable
private fun SettingListRow(
    title: String,
    trailingText: String? = null,
    danger: Boolean = false,
    centered: Boolean = false,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (danger) Color(0xFFEA4335) else Color.Black
    Surface(
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = if (centered) Modifier else Modifier.weight(1f)
            )
            if (!centered) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!trailingText.isNullOrEmpty()) {
                        Text(trailingText, color = Color.Gray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (showChevron) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}



