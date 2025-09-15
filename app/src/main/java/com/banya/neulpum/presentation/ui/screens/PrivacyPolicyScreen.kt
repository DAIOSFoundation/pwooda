package com.banya.neulpum.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("개인정보처리방침") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF10A37F),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 헤더
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10A37F))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "개인정보처리방침",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "(주)반야에이아이",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 개인정보처리방침 내용
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 1. 개인정보의 수집 및 이용 목적
                    Text(
                        text = "1. 개인정보의 수집 및 이용 목적",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "회사는 서비스 제공을 위해 아래 개인정보를 수집합니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 테이블 형태로 표시
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "수집 항목",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "수집 목적",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Google/Kakao 로그인 정보",
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "회원 식별, 서비스 이용 및 관리",
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "이메일",
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "회원 인증, 계정 관리",
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 2. 개인정보 수집 방법
                    Text(
                        text = "2. 개인정보 수집 방법",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 서비스 내 로그인 및 회원가입 과정에서 수집\n• 사용자가 직접 입력하거나 OAuth 인증을 통해 수집",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 3. 개인정보의 보유 및 이용 기간
                    Text(
                        text = "3. 개인정보의 보유 및 이용 기간",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 회원 탈퇴 시 즉시 파기\n• 관계 법령에 따라 별도 보관이 필요한 경우 해당 기간 동안 보관",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 4. 개인정보의 제3자 제공
                    Text(
                        text = "4. 개인정보의 제3자 제공",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 회사는 이용자의 동의 없이 개인정보를 외부에 제공하지 않습니다.\n• 단, 법령에 의해 요구되는 경우에는 예외로 합니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 5. 개인정보의 파기
                    Text(
                        text = "5. 개인정보의 파기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 전자적 파일: 복구 불가능한 방법으로 삭제\n• 종이 문서: 분쇄 등 물리적 방법으로 파기",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 6. 이용자의 권리 및 행사 방법
                    Text(
                        text = "6. 이용자의 권리 및 행사 방법",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 개인정보 열람, 정정, 삭제, 처리정지 요구 가능\n• 문의 이메일: dev@banya.ai",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 7. 개인정보 보호를 위한 기술적·관리적 조치
                    Text(
                        text = "7. 개인정보 보호를 위한 기술적·관리적 조치",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• SSL 암호화 및 안전한 비밀번호 저장\n• 내부 직원 접근 권한 최소화 및 보안 교육",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 8. 쿠키(Cookie) 사용
                    Text(
                        text = "8. 쿠키(Cookie) 사용",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 서비스 개선 및 로그인 유지 목적 사용\n• 브라우저 설정에서 쿠키 저장 거부 가능",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 9. 개인정보 처리방침 변경 시 고지
                    Text(
                        text = "9. 개인정보 처리방침 변경 시 고지",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 변경 시 서비스 내 공지 및 앱스토어 업데이트 반영",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
