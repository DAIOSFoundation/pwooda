package com.banya.pwooda.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Close
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.*
import android.util.Log
import android.content.Context
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banya.pwooda.R
import com.banya.pwooda.ui.components.CameraComponent
import com.banya.pwooda.ui.components.SpeechRecognitionComponent
import com.banya.pwooda.viewmodel.GeminiState
import com.banya.pwooda.viewmodel.GeminiViewModel
import kotlinx.coroutines.launch
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.offset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: GeminiViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showCamera by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var currentQuestion by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    
    val state by viewModel.state.collectAsState()
    
    // 음성인식 런처
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                currentQuestion = results[0]
                // 음성인식이 끝나면 바로 Gemini에게 질문 (캡처된 이미지와 함께)
                Log.d("MainScreen", "음성인식으로 askGemini 호출 - 이미지: ${if (capturedImage != null) "있음" else "없음"}")
                scope.launch {
                    viewModel.askGemini(results[0], capturedImage) // 캡처된 이미지 전달
                }
            } else {
                Log.d("SpeechRecognition", "음성인식 결과가 비어있습니다.")
            }
        } else {
            Log.e("SpeechRecognition", "음성인식에 실패했습니다. (취소 또는 오류)")
        }
        
        // 음성 인식 완료/취소 후 상태 초기화 (항상 실행)
        isListening = false
        viewModel.stopListening() // 음성 인식 종료 상태 설정
        Log.d("MainScreen", "음성 인식 완료/취소 - 상태 초기화됨")
    }
    
    // 초기화
    LaunchedEffect(Unit) {
        // TODO: 실제 API 키로 교체 필요
        viewModel.initializeGemini("AIzaSyCdT94bijeNvNvfIlJSBepxoff7984nmoY")
        viewModel.initializeTTS(context)
    }
    
    // AI 응답 버블이 나타나면 촬영된 이미지 숨기기
    LaunchedEffect(state.shouldShowChatBubble) {
        if (state.shouldShowChatBubble) {
            capturedImage = null // 챗 버블이 표시되면 촬영된 이미지 초기화
            Log.d("MainScreen", "챗 버블 표시됨 - capturedImage 초기화됨")
        }
    }

    // 자동 음성 인식 감지 및 시작
    LaunchedEffect(state.isListening) {
        if (state.isListening && !isListening && !state.isLoading && !state.isSpeaking && !state.isVoiceDownloading && !state.hasTTSError) {
            Log.d("MainScreen", "자동 음성 인식 시작 감지됨 - 상태 확인: isLoading=${state.isLoading}, isSpeaking=${state.isSpeaking}, isVoiceDownloading=${state.isVoiceDownloading}, hasTTSError=${state.hasTTSError}")
            
            // 추가 안전 검사: TTS가 완전히 끝났고 오류가 없는지 확인
            if (!state.isSpeaking && !state.isVoiceDownloading && !state.hasTTSError) {
                isListening = true
                
                // 음성 인식 시작
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "질문을 말씀해 주세요. 말씀을 마치시면 잠시 기다려주세요.")
                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    
                    // 음성 인식 대기 시간 관련 설정
                    putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000) // 최소 음성 길이 2초
                    putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000) // 완전 무음 6초 후 종료
                    putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000) // 부분 무음 4초 후 종료
                    putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000) // 가능한 완료 무음 3초 후 종료
                }
                speechLauncher.launch(intent)
                Log.d("MainScreen", "자동 음성 인식 시작됨")
            } else {
                if (state.hasTTSError) {
                    Log.d("MainScreen", "TTS 오류가 발생했으므로 자동 음성 인식 건너뜀")
                } else {
                    Log.d("MainScreen", "TTS가 아직 진행 중이므로 자동 음성 인식 건너뜀")
                }
            }
        } else if (state.hasTTSError) {
            Log.d("MainScreen", "TTS 오류 상태이므로 자동 음성 인식 조건 불만족")
        }
    }
    
    // 얼굴 인식 콜백 처리 (MainActivity에서 처리됨)
    // 실제 얼굴 인식은 MainActivity의 FaceDetectionService에서 처리됩니다
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 배경 이미지
        Image(
            painter = painterResource(id = R.drawable.totoro),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // 반투명 오버레이
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
        
        if (showCamera) {
            // 카메라 화면
            CameraComponent(
                onImageCaptured = { bitmap ->
                    Log.d("MainScreen", "이미지 캡처됨: ${if (bitmap != null) "성공" else "실패"}")
                    capturedImage = bitmap
                    showCamera = false
                    viewModel.stopCamera() // 카메라 비활성화 상태 설정
                    // 이미지 촬영 후 자동으로 음성인식 시작
                    if (!isListening) {
                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "질문을 말씀해 주세요. 말씀을 마치시면 잠시 기다려주세요.")
                            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            
                            // 음성 인식 대기 시간 관련 설정
                            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000) // 최소 음성 길이 2초
                            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000) // 완전 무음 6초 후 종료
                            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000) // 부분 무음 4초 후 종료
                            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000) // 가능한 완료 무음 3초 후 종료
                        }
                        speechLauncher.launch(intent)
                        isListening = true
                        viewModel.startListening() // 음성 인식 시작 상태 설정
                    }
                },
                onError = { error ->
                    // 에러 처리 - 사용자에게 알림
                    Log.e("Camera", error)
                    showCamera = false
                    viewModel.stopCamera() // 카메라 비활성화 상태 설정
                }
            )
        } else {
            // 메인 UI
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 타이틀 + CI
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Friend",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 스크롤 가능한 컨텐츠 영역
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                    
                    // Gemini 응답 표시 (최상단)
                    val animatedDots = remember { mutableStateOf(1) }
                    if (state.isLoading || state.isVoiceDownloading) {
                        // ... 점 애니메이션
                        LaunchedEffect(state.isLoading, state.isVoiceDownloading) {
                            while (state.isLoading || state.isVoiceDownloading) {
                                kotlinx.coroutines.delay(400)
                                animatedDots.value = if (animatedDots.value == 3) 1 else animatedDots.value + 1
                            }
                            animatedDots.value = 1
                        }
                        val labelText = if (state.isLoading) "thinking" else "voice generating"
                        val dots = ".".repeat(animatedDots.value)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .padding(top = 30.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Black.copy(alpha = 0.85f),
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$labelText $dots",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else if (state.shouldShowChatBubble) { // 챗 버블 표시 여부로 변경
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 30.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.9f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = state.response,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Black
                                    )
                                    // imagePath가 있으면 설명 아래에 이미지 출력
                                    val product = state.currentProduct
                                    if (product?.imagePath != null) {
                                        val imagePath = product.imagePath
                                        if (imagePath != null && imagePath.startsWith("drawable/")) {
                                            val resName = imagePath.removePrefix("drawable/").removeSuffix(".png")
                                            val context = LocalContext.current
                                            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                                            if (resId != 0) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Image(
                                                    painter = painterResource(id = resId),
                                                    contentDescription = "제품 이미지",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(140.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (state.isSpeaking) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "🔊 TTS 재생 중...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    // 제품 이미지 표시 (이미지가 있는 제품인 경우)
                                    if (state.showProductImage && state.productImageResourceName != null) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        val context = LocalContext.current
                                        val resourceId = context.resources.getIdentifier(
                                            state.productImageResourceName,
                                            "raw",
                                            context.packageName
                                        )
                                        if (resourceId != 0) {
                                            Image(
                                                painter = painterResource(id = resourceId),
                                                contentDescription = "제품 이미지",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                    
                                    // 신제품 이미지 표시 (관련 신제품이 있는 경우)
                                    if (state.showNewProductImage && state.newProductImageResourceName != null) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "신제품 런칭!",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val context = LocalContext.current
                                        val resourceId = context.resources.getIdentifier(
                                            state.newProductImageResourceName,
                                            "raw",
                                            context.packageName
                                        )
                                        if (resourceId != 0) {
                                            Image(
                                                painter = painterResource(id = resourceId),
                                                contentDescription = "신제품 이미지",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                    
                                    // 결제 버튼 (제품 인식 시에만 표시)
                                    if (state.showPaymentButton && state.currentProduct != null) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.showPaymentQRCode() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Payment,
                                                contentDescription = "결제",
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("네이버페이로 결제하기 (${state.currentProduct!!.price}원)")
                                        }
                                    }
                                }
                            }
                            // 닫기 버튼
                            IconButton(
                                onClick = {
                                    viewModel.hideChatBubble()
                                    capturedImage = null // 닫기 버튼 클릭 시 촬영 이미지 초기화
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 5.dp, y = 27.dp) // Card의 상단 우측에 위치시키기 위한 오프셋 (조정됨)
                                    .size(36.dp) // 크기 조정 (확대됨)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)) // 색상 검정색으로 변경
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "닫기",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp) // 아이콘 크기 조정 (확대됨)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 촬영된 이미지 썸네일 표시 (원형) - AI 응답 버블이 없을 때만 표시
                    if (capturedImage != null && !state.shouldShowChatBubble) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color.White, shape = CircleShape)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = capturedImage!!.asImageBitmap(),
                                    contentDescription = "촬영된 이미지 썸네일",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 에러 표시
                    if (state.error.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Red.copy(alpha = 0.9f)
                            )
                        ) {
                            Text(
                                text = state.error,
                                modifier = Modifier.padding(16.dp),
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    

                    
                    } // 스크롤 가능한 컨텐츠 영역 끝
                    
                    Spacer(modifier = Modifier.height(16.dp))
                } // Column 끝
            
            // 하단 고정 입력 컨트롤
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 40.dp) // 하단 여백
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                    // 질문 입력과 버튼들을 한 줄에 배치
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 질문 입력 (확장 가능)
                        OutlinedTextField(
                            value = currentQuestion,
                            onValueChange = { currentQuestion = it },
                            label = { Text("질문을 입력하세요") },
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), // 둥근 모퉁이
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Send
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = {
                                    if (currentQuestion.isNotEmpty() && !state.isLoading) {
                                        // 모든 TTS 중지 (AI 응답 + 환영 메시지)
                                        viewModel.stopAllTTS()
                                        Log.d("MainScreen", "텍스트 입력으로 askGemini 호출 - 이미지: ${if (capturedImage != null) "있음" else "없음"}")
                                        scope.launch {
                                            viewModel.askGemini(currentQuestion, capturedImage)
                                        }
                                    }
                                }
                            )
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // 카메라 버튼
                        IconButton(
                            onClick = { 
                                // 모든 TTS 중지 (AI 응답 + 환영 메시지)
                                viewModel.stopAllTTS()
                                viewModel.hideChatBubble() // 카메라 촬영 시 챗 버블 숨김
                                capturedImage = null // 새로운 촬영 시작 전에 기존 이미지 초기화
                                showCamera = true
                                viewModel.startCamera() // 카메라 활성화 상태 설정
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Camera,
                                contentDescription = "카메라",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // 음성인식 버튼 (mic.png 사용)
                        IconButton(
                            onClick = {
                                if (!isListening) {
                                    // 모든 TTS 중지 (AI 응답 + 환영 메시지)
                                    viewModel.stopAllTTS()
                                    viewModel.hideChatBubble() // 음성 인식 시작 시 챗 버블 숨김
                                    // 음성인식 시작 시 이미지 초기화
                                    capturedImage = null
                                    startSpeechRecognition(context, speechLauncher)
                                    isListening = true
                                    viewModel.startListening() // 음성 인식 시작 상태 설정
                                }
                            },
                            enabled = !isListening,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.mic),
                                contentDescription = "음성 인식",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } // 하단 고정 입력 컨트롤 끝
            } // Box 끝
        }
        
        // QR 코드 다이얼로그
        if (state.showQRCode && state.qrCodeBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "네이버페이 QR 코드",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (state.currentProduct != null) {
                            Text(
                                text = "${state.currentProduct!!.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black
                            )
                            Text(
                                text = "${state.currentProduct!!.price}원",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Image(
                            bitmap = state.qrCodeBitmap!!.asImageBitmap(),
                            contentDescription = "네이버페이 QR 코드",
                            modifier = Modifier.size(200.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "네이버페이 앱으로 QR 코드를 스캔하여 결제를 완료하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { viewModel.hidePaymentQRCode() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "닫기",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("닫기")
                        }
                    }
                }
            }
        }
    }
}

private fun startSpeechRecognition(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR") // 한국어 (대한민국)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR") // 언어 선호도 설정
        putExtra(RecognizerIntent.EXTRA_PROMPT, "질문을 말씀해 주세요. 말씀을 마치시면 잠시 기다려주세요.")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // 부분 결과 허용
        
        // 음성 인식 대기 시간 관련 설정
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000) // 최소 음성 길이 2초
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000) // 완전 무음 6초 후 종료
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000) // 부분 무음 4초 후 종료
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000) // 가능한 완료 무음 3초 후 종료
    }
    
    launcher.launch(intent)
}

@Preview(showBackground = true, name = "MainScreen Preview")
@Composable
fun MainScreenPreview() {
    // ViewModel을 미리 생성하지 않고, 기본값으로 전달
    MainScreen()
}