package com.banya.neulpum.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun PermissionDialog(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color = Color(0xFF10A37F),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "허용",
    dismissText: String = "거부"
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 아이콘
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = iconColor
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 제목
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 설명
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 거부 버튼
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Gray
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        )
                    ) {
                        Text(
                            text = dismissText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // 허용 버튼
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10A37F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = confirmText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionDialog(
        title = "카메라 권한이 필요합니다",
        description = "AI와 대화할 때 사진을 찍어서 질문하기 위해 카메라 접근 권한이 필요합니다.\n\n예: 상품 사진을 찍어서 '이 제품에 대해 알려주세요'라고 질문\n\n설정에서 권한을 허용해주세요.",
        icon = Icons.Default.CameraAlt,
        iconColor = Color(0xFF10A37F),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = "설정",
        dismissText = "나중에"
    )
}

@Composable
fun MicrophonePermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionDialog(
        title = "마이크 권한이 필요합니다",
        description = "음성으로 AI와 대화하기 위해 마이크 접근 권한이 필요합니다.\n\n예: 음성으로 질문하고 AI의 답변을 음성으로 듣기\n\n설정에서 권한을 허용해주세요.",
        icon = Icons.Default.Mic,
        iconColor = Color(0xFF10A37F),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = "설정",
        dismissText = "나중에"
    )
}

@Composable
fun StoragePermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionDialog(
        title = "저장소 권한이 필요합니다",
        description = "사진을 저장하고 불러오기 위해 저장소 접근 권한이 필요합니다.\n\n예: 찍은 사진을 갤러리에 저장하거나 갤러리에서 사진 선택\n\n설정에서 권한을 허용해주세요.",
        icon = Icons.Default.Storage,
        iconColor = Color(0xFF10A37F),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = "설정",
        dismissText = "나중에"
    )
}
