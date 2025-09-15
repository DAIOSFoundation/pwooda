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
fun TermsOfServiceScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이용약관") },
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
                        text = "늘품 서비스 이용약관",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "회사명: (주)반야에이아이",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "서비스명: 늘품",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 약관 내용
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 제1조
                    Text(
                        text = "제1조 (목적)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "본 약관은 (주)반야에이아이(이하 \"회사\")가 제공하는 늘품 서비스(이하 \"서비스\")의 이용조건, 절차, 회사와 이용자의 권리·의무 및 책임사항을 규정함을 목적으로 합니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 제2조
                    Text(
                        text = "제2조 (약관의 효력 및 변경)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 본 약관은 서비스 화면 또는 홈페이지에 게시함으로써 효력이 발생합니다.\n2. 회사는 관련 법령을 위배하지 않는 범위 내에서 약관을 변경할 수 있으며, 변경 시 사전 공지합니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 제3조
                    Text(
                        text = "제3조 (서비스 이용)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 이용자는 회사가 정한 절차에 따라 회원 가입 및 로그인을 해야 서비스를 이용할 수 있습니다.\n2. 서비스는 Google 계정 및 Kakao 계정을 통한 로그인을 지원합니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 제4조
                    Text(
                        text = "제4조 (회원의 의무)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 이용자는 회원가입 시 제공한 정보가 정확하고 최신 정보임을 보증해야 합니다.\n2. 계정 정보(로그인 정보, 비밀번호)는 안전하게 관리해야 하며, 제3자에게 제공할 수 없습니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 제5조
                    Text(
                        text = "제5조 (서비스 제공의 제한 및 중단)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 회사는 서비스 유지, 보수, 업데이트를 위해 서비스를 일시적으로 중단할 수 있습니다.\n2. 천재지변, 시스템 장애 등 불가항력의 사유로 서비스 제공이 불가능한 경우 회사는 책임을 지지 않습니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 제6조
                    Text(
                        text = "제6조 (저작권 및 지적재산권)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. 서비스와 관련된 모든 콘텐츠 및 프로그램의 저작권은 회사에 귀속됩니다.\n2. 이용자는 회사의 사전 동의 없이 콘텐츠를 복제, 배포, 전송, 출판, 2차적 저작물 작성, 영리목적 이용을 할 수 없습니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 제7조
                    Text(
                        text = "제7조 (준거법 및 관할)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10A37F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "본 약관과 관련된 분쟁은 대한민국 법령을 준거법으로 하며, 관할 법원은 민사소송법에 따른 관할 법원으로 합니다.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
