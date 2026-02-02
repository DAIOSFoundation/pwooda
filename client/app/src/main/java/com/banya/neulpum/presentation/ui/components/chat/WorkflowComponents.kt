package com.banya.neulpum.presentation.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class WorkflowStep(
    val stage: String,
    val detail: String,
    val tool: String? = null,
    val result: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun WorkflowStepsCard(
    steps: List<WorkflowStep>,
    currentTool: String?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF64B5F6),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isLoading) "..." else "처리 완료",
                    fontSize = 16.sp,
                    color = if (isLoading) Color(0xFF64B5F6) else Color(0xFF4CAF50)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            steps.forEachIndexed { index, step ->
                WorkflowStepItem(
                    step = step,
                    isLast = index == steps.size - 1,
                    isLoading = isLoading && index == steps.size - 1
                )
                if (index < steps.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (currentTool != null && isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF64B5F6),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔧 $currentTool 실행 중...",
                        fontSize = 14.sp,
                        color = Color(0xFF64B5F6)
                    )
                }
            }
        }
    }
}

@Composable
fun WorkflowStepItem(
    step: WorkflowStep,
    isLast: Boolean,
    isLoading: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = getStageDisplayName(step.stage),
                fontSize = 14.sp,
                color = Color.Black
            )
            if (step.detail.isNotEmpty()) {
                Text(
                    text = step.detail,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (step.tool != null) {
                Text(
                    text = "도구: ${step.tool}",
                    fontSize = 12.sp,
                    color = Color(0xFF64B5F6)
                )
            }
        }
    }
}

fun getStageDisplayName(stage: String): String {
    return when (stage) {
        "init" -> "요청 수신"
        "plan" -> "계획 수립"
        "tool" -> "도구 실행"
        "tool_result" -> "결과 처리"
        "summarize" -> "결과 요약"
        "final" -> "완료"
        else -> stage
    }
}


